package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicUserProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserGrowthService userGrowthService;

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
}
