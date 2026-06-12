package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Interaction;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.comment.CommentCreateRequest;
import com.skatehub.pojo.post.PostCreateRequest;
import com.skatehub.pojo.post.PostUpdateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final Set<String> PUBLIC_SORTS = Set.of("latest", "hot", "likes", "comments");

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final InteractionRepository interactionRepository;
    private final MessageNotifyService messageNotifyService;
    private final UserGrowthService userGrowthService;

    public PageResult<Map<String, Object>> list(String keyword, String category, String sort, Integer page, Integer size) {
        int currentPage = InputValidator.page(page);
        int currentSize = InputValidator.size(size);
        String normalizedKeyword = keyword == null ? "" : InputValidator.likeKeyword(keyword, 50, "搜索关键词");
        String normalizedCategory = category == null ? "" : InputValidator.optionalSimpleText(category, 30, "分类");
        String normalizedSort = InputValidator.optionalAllowed(sort, PUBLIC_SORTS, "latest", "排序方式");
        PageRequest pageRequest = PageRequest.of(currentPage - 1, currentSize);
        Page<Post> postPage = postRepository.searchPublicPosts(
                normalizedKeyword,
                normalizedCategory,
                normalizedSort,
                pageRequest
        );
        List<Map<String, Object>> list = postPage.getContent().stream().map(this::toPostVO).toList();
        return new PageResult<>(postPage.getTotalElements(), list);
    }

    public Map<String, Object> detail(Long postId) {
        InputValidator.positiveId(postId, "帖子ID");
        Post post = getPost(postId);
        return toPostVO(post);
    }

    public Post create(CurrentUser currentUser, PostCreateRequest request) {
        Long currentUserId = requireCurrentUserId(currentUser);
        Post post = new Post();
        post.setUserId(currentUserId);
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setCategory(request.getCategory());
        Post saved = postRepository.save(post);
        userGrowthService.grantExp(currentUserId, "POST_CREATE", "POST", saved.getPostId(), 20);
        return saved;
    }

    public Post update(CurrentUser currentUser, Long postId, PostUpdateRequest request) {
        InputValidator.positiveId(postId, "帖子ID");
        Long currentUserId = requireCurrentUserId(currentUser);
        Post post = getPost(postId);
        if (!post.getUserId().equals(currentUserId)) {
            throw new BizException("只能编辑自己发布的帖子");
        }
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setCategory(request.getCategory());
        return postRepository.save(post);
    }

    public void delete(CurrentUser currentUser, Long postId, boolean isAdmin) {
        InputValidator.positiveId(postId, "帖子ID");
        Long currentUserId = requireCurrentUserId(currentUser);
        boolean adminMode = requireAdminModeIfRequested(currentUser, isAdmin);
        Post post = getPost(postId);
        if (!adminMode && !post.getUserId().equals(currentUserId)) {
            throw new BizException("无权删除该帖子");
        }
        postRepository.delete(post);
    }

    public List<Map<String, Object>> comments(Long postId) {
        InputValidator.positiveId(postId, "帖子ID");
        getPost(postId);
        return commentRepository.findByPostIdOrderByCreateTimeAsc(postId).stream()
                .map(this::toCommentVO)
                .toList();
    }

    public Comment addComment(CurrentUser currentUser, Long postId, CommentCreateRequest request) {
        InputValidator.positiveId(postId, "帖子ID");
        Long currentUserId = requireCurrentUserId(currentUser);
        Post post = getPost(postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        userGrowthService.grantExp(currentUserId, "POST_COMMENT", "COMMENT", saved.getCommentId(), 8);
        if (!post.getUserId().equals(currentUserId)) {
            messageNotifyService.send(post.getUserId(), "COMMENT", "你的帖子收到新评论：" + post.getTitle(), "POST", post.getPostId());
        }
        return saved;
    }

    public void deleteComment(CurrentUser currentUser, Long commentId, boolean isAdmin) {
        Long currentUserId = requireCurrentUserId(currentUser);
        boolean adminMode = requireAdminModeIfRequested(currentUser, isAdmin);
        InputValidator.positiveId(commentId, "评论ID");
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new BizException("评论不存在"));
        if (adminMode || comment.getUserId().equals(currentUserId)) {
            commentRepository.delete(comment);
            return;
        }
        Post post = getPost(comment.getPostId());
        if (post.getUserId().equals(currentUserId)) {
            commentRepository.delete(comment);
            return;
        }
        throw new BizException("无权删除该评论");
    }

    public Map<String, Object> toggleLike(CurrentUser currentUser, Long postId) {
        return toggleInteraction(currentUser, postId, "LIKE");
    }

    public Map<String, Object> toggleCollect(CurrentUser currentUser, Long postId) {
        return toggleInteraction(currentUser, postId, "COLLECT");
    }

    public Map<String, Object> toggleWatchLater(CurrentUser currentUser, Long postId) {
        return toggleInteraction(currentUser, postId, "WATCH_LATER");
    }

    public Post top(Long postId, String top) {
        InputValidator.positiveId(postId, "帖子ID");
        String value = InputValidator.requiredAllowed(top, Set.of("0", "1"), "置顶值");
        Post post = getPost(postId);
        post.setIsTop(value);
        return postRepository.save(post);
    }

    private Map<String, Object> toggleInteraction(CurrentUser currentUser, Long postId, String type) {
        Long currentUserId = requireCurrentUserId(currentUser);
        Post post = getPost(postId);
        Interaction exists = interactionRepository
                .findByUserIdAndTargetTypeAndTargetIdAndType(currentUserId, "POST", postId, type)
                .orElse(null);
        boolean active;
        if (exists == null) {
            Interaction interaction = new Interaction();
            interaction.setUserId(currentUserId);
            interaction.setTargetType("POST");
            interaction.setTargetId(postId);
            interaction.setType(type);
            interactionRepository.save(interaction);
            active = true;
            if (!post.getUserId().equals(currentUserId) && "LIKE".equals(type)) {
                messageNotifyService.send(post.getUserId(), "LIKE", "你的帖子收到一个赞：" + post.getTitle(), "POST", post.getPostId());
            }
            if ("LIKE".equals(type)) {
                userGrowthService.grantExp(currentUserId, "POST_LIKE", "POST", postId, 2);
            }
        } else {
            interactionRepository.delete(exists);
            active = false;
        }
        long likeCount = interactionRepository.countByTargetTypeAndTargetIdAndType("POST", postId, "LIKE");
        long collectCount = interactionRepository.countByTargetTypeAndTargetIdAndType("POST", postId, "COLLECT");
        post.setLikeCount((int) likeCount);
        post.setCollectCount((int) collectCount);
        postRepository.save(post);

        Map<String, Object> result = new HashMap<>();
        result.put("active", active);
        result.put("likeCount", likeCount);
        result.put("collectCount", collectCount);
        return result;
    }

    private Post getPost(Long postId) {
        InputValidator.positiveId(postId, "帖子ID");
        return postRepository.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
    }

    private Long requireCurrentUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private boolean requireAdminModeIfRequested(CurrentUser currentUser, boolean isAdmin) {
        boolean adminMode = isAdmin && currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.role());
        if (isAdmin && !adminMode) {
            throw new BizException("无管理员操作权限");
        }
        return adminMode;
    }

    private Map<String, Object> toPostVO(Post post) {
        Map<String, Object> map = new HashMap<>();
        map.put("postId", post.getPostId());
        map.put("userId", post.getUserId());
        map.put("title", post.getTitle());
        map.put("content", post.getContent());
        map.put("images", post.getImages());
        map.put("category", post.getCategory());
        map.put("likeCount", post.getLikeCount());
        map.put("collectCount", post.getCollectCount());
        map.put("isTop", post.getIsTop());
        map.put("createTime", post.getCreateTime());
        map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
        User user = userRepository.findById(post.getUserId()).orElse(null);
        map.put("authorName", user == null ? "已注销用户" : user.getUsername());
        map.put("authorAvatar", user == null ? null : user.getAvatar());
        if (user != null) {
            userGrowthService.fillGrowthInfo(user);
            map.put("authorLevel", user.getLevel());
        } else {
            map.put("authorLevel", 1);
        }
        return map;
    }

    private Map<String, Object> toCommentVO(Comment comment) {
        Map<String, Object> map = new HashMap<>();
        map.put("commentId", comment.getCommentId());
        map.put("postId", comment.getPostId());
        map.put("userId", comment.getUserId());
        map.put("parentId", comment.getParentId());
        map.put("content", comment.getContent());
        map.put("createTime", comment.getCreateTime());
        User user = userRepository.findById(comment.getUserId()).orElse(null);
        map.put("username", user == null ? "已注销用户" : user.getUsername());
        map.put("avatar", user == null ? null : user.getAvatar());
        return map;
    }
}
