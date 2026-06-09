package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.Interaction;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivitySignStatus;
import com.skatehub.util.ActivityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ActivityRepository activityRepository;
    private final CommunityBulletinRepository communityBulletinRepository;
    private final InteractionRepository interactionRepository;
    private final CommentRepository commentRepository;
    private final ActivitySignRepository activitySignRepository;
    private final PostService postService;
    private final ActivityService activityService;
    private final CommunityBulletinService communityBulletinService;

    public Map<String, Object> recommendForUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        Set<String> categories = collectPostCategories(userId);
        Set<String> cities = collectCities(userId);
        String style = user == null ? "" : normalize(user.getSkateStyle());

        List<Post> visiblePosts = postRepository.findAll().stream()
                .filter(post -> !"1".equals(post.getIsTop()) || true)
                .collect(Collectors.toList());
        List<Map<String, Object>> postList = scorePosts(visiblePosts, categories, style).stream()
                .limit(6)
                .map(post -> postService.detail(post.getPostId()))
                .collect(Collectors.toList());

        List<Activity> visibleActivities = activityRepository.findAll().stream()
                .filter(item -> ActivityReviewStatus.APPROVED.equals(item.getReviewStatus()))
                .filter(item -> {
                    String status = item.getActivityStatus();
                    return ActivityStatus.SIGNUP_OPEN.equals(status)
                            || ActivityStatus.FULL.equals(status)
                            || ActivityStatus.CANCELED.equals(status)
                            || ActivityStatus.ENDED.equals(status);
                })
                .collect(Collectors.toList());
        List<Map<String, Object>> activityList = scoreActivities(visibleActivities, cities, style).stream()
                .limit(4)
                .map(item -> activityService.list(1, 1, userId, null, null, item.getTitle(), false).getList().stream().findFirst().orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toList());

        List<Map<String, Object>> bulletinList = scoreBulletins(
                communityBulletinService.listPublicApprovedAll(), cities, style
        ).stream().limit(5).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("posts", postList.isEmpty() ? fallbackPosts() : postList);
        result.put("activities", activityList.isEmpty() ? fallbackActivities(userId) : activityList);
        result.put("bulletins", bulletinList.isEmpty() ? fallbackBulletins() : bulletinList);
        return result;
    }

    public List<Map<String, Object>> recommendedPostsForUser(Long userId, int limit) {
        Map<String, Object> data = recommendForUser(userId);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("posts");
        return list.stream().limit(Math.max(1, limit)).collect(Collectors.toList());
    }

    private List<Map<String, Object>> fallbackPosts() {
        return postService.list("", "", "hot", 1, 6).getList();
    }

    private List<Map<String, Object>> fallbackActivities(Long userId) {
        return activityService.list(1, 4, userId, null, null, null, false).getList();
    }

    private List<Map<String, Object>> fallbackBulletins() {
        return communityBulletinService.listPublicApproved(5);
    }

    private Set<String> collectPostCategories(Long userId) {
        Set<String> categories = new HashSet<>();
        List<Interaction> interactions = interactionRepository
                .findByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "LIKE");
        interactions.addAll(interactionRepository.findByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "COLLECT"));
        for (Interaction interaction : interactions) {
            postRepository.findById(interaction.getTargetId()).ifPresent(post -> {
                if (StringUtils.hasText(post.getCategory())) categories.add(post.getCategory().trim());
            });
        }
        List<Comment> myComments = commentRepository.findAll().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .limit(50)
                .toList();
        for (Comment comment : myComments) {
            postRepository.findById(comment.getPostId()).ifPresent(post -> {
                if (StringUtils.hasText(post.getCategory())) categories.add(post.getCategory().trim());
            });
        }
        return categories;
    }

    private Set<String> collectCities(Long userId) {
        Set<String> cities = new HashSet<>();
        List<ActivitySign> signs = activitySignRepository.findByUserIdOrderBySignTimeDesc(userId);
        for (ActivitySign sign : signs.stream().limit(50).toList()) {
            activityRepository.findById(sign.getActivityId()).ifPresent(activity -> {
                if (StringUtils.hasText(activity.getCity())) cities.add(activity.getCity().trim());
            });
        }
        List<Activity> published = activityRepository.findByUserIdOrderByCreateTimeDesc(userId);
        for (Activity activity : published.stream().limit(20).toList()) {
            if (StringUtils.hasText(activity.getCity())) cities.add(activity.getCity().trim());
        }
        return cities;
    }

    private List<Post> scorePosts(List<Post> posts, Set<String> categories, String style) {
        Map<Long, Integer> scoreMap = new HashMap<>();
        for (Post post : posts) {
            int score = 0;
            if (!categories.isEmpty() && categories.contains(post.getCategory())) score += 30;
            String text = normalize(post.getTitle()) + " " + normalize(post.getContent());
            if (StringUtils.hasText(style) && text.contains(style)) score += 16;
            score += safe(post.getLikeCount()) * 2 + safe(post.getCollectCount());
            scoreMap.put(post.getPostId(), score);
        }
        return posts.stream()
                .sorted(Comparator.<Post>comparingInt(p -> scoreMap.getOrDefault(p.getPostId(), 0)).reversed()
                        .thenComparing(Post::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private List<Activity> scoreActivities(List<Activity> activities, Set<String> cities, String style) {
        Map<Long, Integer> scoreMap = new HashMap<>();
        for (Activity activity : activities) {
            int score = 0;
            if (!cities.isEmpty() && cities.contains(trim(activity.getCity()))) score += 28;
            String text = normalize(activity.getTitle()) + " " + normalize(activity.getContent()) + " " + normalize(activity.getActivityType());
            if (StringUtils.hasText(style) && text.contains(style)) score += 16;
            score += safe(activity.getSignNum()) * 3;
            scoreMap.put(activity.getActivityId(), score);
        }
        return activities.stream()
                .sorted(Comparator.<Activity>comparingInt(a -> scoreMap.getOrDefault(a.getActivityId(), 0)).reversed()
                        .thenComparing(Activity::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> scoreBulletins(List<Map<String, Object>> bulletins, Set<String> cities, String style) {
        List<Map<String, Object>> list = new ArrayList<>(bulletins);
        list.sort((a, b) -> Integer.compare(bulletinScore(a, cities, style), bulletinScore(b, cities, style)));
        return list;
    }

    private int bulletinScore(Map<String, Object> item, Set<String> cities, String style) {
        int score = 0;
        String text = normalize((String) item.get("title")) + " " + normalize((String) item.get("content"));
        for (String city : cities) {
            if (StringUtils.hasText(city) && text.contains(normalize(city))) score += 22;
        }
        if (StringUtils.hasText(style) && text.contains(style)) score += 12;
        return score;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
