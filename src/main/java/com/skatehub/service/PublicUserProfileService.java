package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.Video;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PublicUserProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserGrowthService userGrowthService;
    private final VideoRepository videoRepository;

    @Autowired
    public PublicUserProfileService(UserRepository userRepository,
                                    PostRepository postRepository,
                                    CommentRepository commentRepository,
                                    UserFollowRepository userFollowRepository,
                                    UserGrowthService userGrowthService,
                                    VideoRepository videoRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userFollowRepository = userFollowRepository;
        this.userGrowthService = userGrowthService;
        this.videoRepository = videoRepository;
    }

    public PublicUserProfileService(UserRepository userRepository,
                                    PostRepository postRepository,
                                    CommentRepository commentRepository,
                                    UserFollowRepository userFollowRepository,
                                    UserGrowthService userGrowthService) {
        this(userRepository, postRepository, commentRepository, userFollowRepository, userGrowthService, null);
    }

    public Map<String, Object> profile(Long userId) {
        InputValidator.positiveId(userId, "userId");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("user not found"));
        userGrowthService.fillGrowthInfo(user);

        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("username", user.getUsername());
        map.put("avatar", user.getAvatar());
        map.put("gender", user.getGender());
        map.put("skateStyle", user.getSkateStyle());
        map.put("bio", user.getBio());
        map.put("level", user.getLevel());
        map.put("exp", user.getExp());
        map.put("postCount", postRepository.countByUserId(userId));
        map.put("followerCount", userFollowRepository.countByFolloweeIdAndStatus(userId, "1"));
        map.put("followingCount", userFollowRepository.countByFollowerIdAndStatus(userId, "1"));
        map.put("creatorSummary", creatorSummary(userId));
        map.put("createTime", user.getCreateTime());
        return map;
    }

    public PageResult<Map<String, Object>> posts(Long userId, Integer page, Integer size) {
        InputValidator.positiveId(userId, "userId");
        if (!userRepository.existsById(userId)) {
            throw new BizException("user not found");
        }
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        Page<Post> postPage = postRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
        return new PageResult<>(postPage.getTotalElements(), postPage.getContent().stream()
                .map(this::toPostSummary)
                .toList());
    }

    private Map<String, Object> toPostSummary(Post post) {
        Map<String, Object> map = new HashMap<>();
        map.put("postId", post.getPostId());
        map.put("title", post.getTitle());
        map.put("category", post.getCategory());
        map.put("createTime", post.getCreateTime());
        map.put("likeCount", post.getLikeCount() == null ? 0 : post.getLikeCount());
        map.put("collectCount", post.getCollectCount() == null ? 0 : post.getCollectCount());
        map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
        return map;
    }

    private Map<String, Object> creatorSummary(Long userId) {
        Map<String, Object> map = new HashMap<>();
        java.util.List<Video> videos = videoRepository == null
                ? java.util.List.of()
                : videoRepository.findByUserIdOrderByCreateTimeDesc(userId);
        java.util.List<Post> posts = postRepository.findByUserIdOrderByCreateTimeDesc(userId);
        java.util.List<Post> tutorials = posts.stream()
                .filter(this::isTutorialPost)
                .limit(6)
                .toList();
        int totalLikes = posts.stream().mapToInt(post -> post.getLikeCount() == null ? 0 : post.getLikeCount()).sum();
        int totalCollects = posts.stream().mapToInt(post -> post.getCollectCount() == null ? 0 : post.getCollectCount()).sum();
        int videoQuality = videos.stream().mapToInt(video -> video.getQualityScore() == null ? 0 : video.getQualityScore()).sum();
        boolean hasCreatorContent = !videos.isEmpty() || !tutorials.isEmpty();

        map.put("hasCreatorContent", hasCreatorContent);
        map.put("videoCount", videos.size());
        map.put("tutorialPostCount", tutorials.size());
        map.put("followerCount", userFollowRepository.countByFolloweeIdAndStatus(userId, "1"));
        map.put("totalEngagement", totalLikes + totalCollects);
        map.put("qualityScore", videos.isEmpty() ? 0 : Math.round(videoQuality * 1.0 / videos.size()));
        map.put("recentVideos", videos.stream().limit(4).map(this::videoSummary).toList());
        map.put("tutorials", tutorials.stream().map(this::toPostSummary).toList());
        return map;
    }

    private boolean isTutorialPost(Post post) {
        String text = ((post.getTitle() == null ? "" : post.getTitle()) + " "
                + (post.getCategory() == null ? "" : post.getCategory()) + " "
                + (post.getContent() == null ? "" : post.getContent())).toLowerCase();
        return text.contains("教程") || text.contains("教学") || text.contains("技巧")
                || text.contains("tutorial") || text.contains("trick") || text.contains("how to");
    }

    private Map<String, Object> videoSummary(Video video) {
        Map<String, Object> map = new HashMap<>();
        map.put("videoId", video.getVideoId());
        map.put("title", video.getTitle());
        map.put("cover", video.getCover());
        map.put("creatorSeries", video.getCreatorSeries());
        map.put("trickTags", video.getTrickTags());
        map.put("qualityScore", video.getQualityScore());
        map.put("createTime", video.getCreateTime());
        return map;
    }
}
