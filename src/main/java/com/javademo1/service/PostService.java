package com.javademo1.service;

import com.javademo1.dao.CommentRepository;
import com.javademo1.dao.InteractionRepository;
import com.javademo1.dao.PostRepository;
import com.javademo1.dao.UserRepository;
import com.javademo1.pojo.Comment;
import com.javademo1.pojo.Interaction;
import com.javademo1.pojo.Post;
import com.javademo1.pojo.User;
import com.javademo1.pojo.comment.CommentCreateRequest;
import com.javademo1.pojo.post.PostCreateRequest;
import com.javademo1.pojo.post.PostUpdateRequest;
import com.javademo1.util.BizException;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final InteractionRepository interactionRepository;
    private final MessageNotifyService messageNotifyService;

    public PageResult<Map<String, Object>> list(String category, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        PageRequest pageRequest = PageRequest.of(currentPage - 1, currentSize);
        Page<Post> postPage = (category == null || category.isBlank())
                ? postRepository.findAllByOrderByIsTopDescCreateTimeDesc(pageRequest)
                : postRepository.findByCategoryOrderByIsTopDescCreateTimeDesc(category, pageRequest);
        List<Map<String, Object>> list = postPage.getContent().stream().map(this::toPostVO).toList();
        return new PageResult<>(postPage.getTotalElements(), list);
    }

    public Map<String, Object> detail(Long postId) {
        Post post = getPost(postId);
        return toPostVO(post);
    }

    public Post create(CurrentUser currentUser, PostCreateRequest request) {
        Post post = new Post();
        post.setUserId(currentUser.id());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setCategory(request.getCategory());
        return postRepository.save(post);
    }

    public Post update(CurrentUser currentUser, Long postId, PostUpdateRequest request) {
        Post post = getPost(postId);
        if (!post.getUserId().equals(currentUser.id())) {
            throw new BizException("只能编辑自己的帖子");
        }
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setCategory(request.getCategory());
        return postRepository.save(post);
    }

    public void delete(CurrentUser currentUser, Long postId, boolean isAdmin) {
        Post post = getPost(postId);
        if (!isAdmin && !post.getUserId().equals(currentUser.id())) {
            throw new BizException("无权限删除");
        }
        postRepository.delete(post);
    }

    public List<Map<String, Object>> comments(Long postId) {
        getPost(postId);
        return commentRepository.findByPostIdOrderByCreateTimeAsc(postId).stream()
                .map(this::toCommentVO)
                .toList();
    }

    public Comment addComment(CurrentUser currentUser, Long postId, CommentCreateRequest request) {
        Post post = getPost(postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUser.id());
        comment.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        if (!post.getUserId().equals(currentUser.id())) {
            messageNotifyService.send(post.getUserId(), "COMMENT", "你的帖子收到新评论：" + post.getTitle());
        }
        return saved;
    }

    public void deleteComment(CurrentUser currentUser, Long commentId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new BizException("评论不存在"));
        if (isAdmin || comment.getUserId().equals(currentUser.id())) {
            commentRepository.delete(comment);
            return;
        }
        Post post = getPost(comment.getPostId());
        if (post.getUserId().equals(currentUser.id())) {
            commentRepository.delete(comment);
            return;
        }
        throw new BizException("无权限删除评论");
    }

    public Map<String, Object> toggleLike(CurrentUser currentUser, Long postId) {
        return toggleInteraction(currentUser, postId, "LIKE");
    }

    public Map<String, Object> toggleCollect(CurrentUser currentUser, Long postId) {
        return toggleInteraction(currentUser, postId, "COLLECT");
    }

    public Post top(Long postId, String top) {
        Post post = getPost(postId);
        post.setIsTop("1".equals(top) ? "1" : "0");
        return postRepository.save(post);
    }

    private Map<String, Object> toggleInteraction(CurrentUser currentUser, Long postId, String type) {
        Post post = getPost(postId);
        Interaction exists = interactionRepository
                .findByUserIdAndTargetTypeAndTargetIdAndType(currentUser.id(), "POST", postId, type)
                .orElse(null);
        boolean active;
        if (exists == null) {
            Interaction interaction = new Interaction();
            interaction.setUserId(currentUser.id());
            interaction.setTargetType("POST");
            interaction.setTargetId(postId);
            interaction.setType(type);
            interactionRepository.save(interaction);
            active = true;
            if (!post.getUserId().equals(currentUser.id()) && "LIKE".equals(type)) {
                messageNotifyService.send(post.getUserId(), "LIKE", "你的帖子收到一个赞：" + post.getTitle());
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
        return postRepository.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
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
