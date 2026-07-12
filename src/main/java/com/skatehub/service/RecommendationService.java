package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.dao.UserTrainingRecordRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.Interaction;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.UserTrainingPlan;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivitySignStatus;
import com.skatehub.util.ActivityStatus;
import com.skatehub.util.PageResult;
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
    private final TrainingPlanRepository trainingPlanRepository;
    private final UserTrainingPlanRepository userTrainingPlanRepository;
    private final UserTrainingRecordRepository userTrainingRecordRepository;

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
        result.put("trainingPlans", recommendTrainingPlans(userId, user, style));
        result.put("nextForYou", buildNextForYou(result));
        return result;
    }

    private Map<String, Object> buildNextForYou(Map<String, Object> source) {
        List<Map<String, Object>> plans = listValue(source.get("trainingPlans"));
        List<Map<String, Object>> posts = listValue(source.get("posts"));
        List<Map<String, Object>> activities = listValue(source.get("activities"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("practiceNext", plans.stream()
                .limit(2)
                .map(item -> nextAction("TRAINING_PLAN", item.get("planId"), text(item.get("planName")),
                        text(item.get("reason")), "/skate", "Start task"))
                .toList());
        result.put("watchNext", posts.stream()
                .filter(item -> containsAny(text(item.get("title")) + " " + text(item.get("category")), "教程", "教学", "技巧", "trick", "tutorial"))
                .limit(2)
                .map(item -> nextAction("POST", item.get("postId"), text(item.get("title")),
                        "Based on your recent community interests", "/community/post/" + item.get("postId"), "Read tutorial"))
                .toList());
        result.put("joinNext", activities.stream()
                .limit(2)
                .map(item -> nextAction("ACTIVITY", item.get("activityId"), text(item.get("title")),
                        "Local and recent activity signal", "/activities/" + item.get("activityId"), "View activity"))
                .toList());
        result.put("reviewSafety", List.of(nextAction("SAFETY", null, "Practice safety check",
                "Before skating, confirm protective gear, lighting, weather, water, and a return plan.",
                "/ai-coach?q=safety%20check", "Ask AI Coach")));
        return result;
    }

    private Map<String, Object> nextAction(String type, Object id, String title, String reason, String routePath, String actionLabel) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("title", title);
        item.put("reason", reason);
        item.put("routePath", routePath);
        item.put("actionLabel", actionLabel);
        return item;
    }

    private List<Map<String, Object>> listValue(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean containsAny(String value, String... needles) {
        String normalized = normalize(value);
        for (String needle : needles) {
            if (normalized.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    public List<Map<String, Object>> recommendedPostsForUser(Long userId, int limit) {
        Map<String, Object> data = recommendForUser(userId);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("posts");
        return list.stream().limit(Math.max(1, limit)).collect(Collectors.toList());
    }

    private List<Map<String, Object>> fallbackPosts() {
        PageResult<Map<String, Object>> result = postService.list("", "", "hot", 1, 6);
        return result == null || result.getList() == null ? List.of() : result.getList();
    }

    private List<Map<String, Object>> fallbackActivities(Long userId) {
        PageResult<Map<String, Object>> result = activityService.list(1, 4, userId, null, null, null, false);
        return result == null || result.getList() == null ? List.of() : result.getList();
    }

    private List<Map<String, Object>> fallbackBulletins() {
        List<Map<String, Object>> result = communityBulletinService.listPublicApproved(5);
        return result == null ? List.of() : result;
    }

    private List<Map<String, Object>> recommendTrainingPlans(Long userId, User user, String style) {
        Set<Long> completedOrActivePlanIds = userTrainingPlanRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(UserTrainingPlan::getPlanId)
                .collect(Collectors.toSet());
        boolean hasTrainingRecords = !userTrainingRecordRepository.findByUserIdOrderByCreateTimeDesc(userId).isEmpty();
        int userLevel = user == null || user.getLevel() == null ? 1 : Math.max(1, user.getLevel());

        List<TrainingPlan> plans = trainingPlanRepository.findByStatusOrderByPlanIdAsc("1");
        Map<Long, Integer> scoreMap = new HashMap<>();
        Map<Long, String> reasonMap = new HashMap<>();
        for (TrainingPlan plan : plans) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            int difficulty = plan.getDifficultyLevel() == null ? 1 : plan.getDifficultyLevel();
            if (Math.abs(difficulty - userLevel) <= 1) {
                score += 36;
                reasons.add("level match");
            }
            String text = normalize(plan.getPlanName()) + " " + normalize(plan.getPlanType()) + " " + normalize(plan.getDescription());
            if (StringUtils.hasText(style) && text.contains(style)) {
                score += 24;
                reasons.add("style match");
            }
            if (!hasTrainingRecords && difficulty <= 2) {
                score += 16;
                reasons.add("starter friendly");
            }
            if (completedOrActivePlanIds.contains(plan.getPlanId())) {
                score -= 50;
                reasons.add("already tried");
            }
            scoreMap.put(plan.getPlanId(), score);
            reasonMap.put(plan.getPlanId(), reasons.isEmpty() ? "balanced next plan" : String.join(", ", reasons));
        }

        return plans.stream()
                .sorted(Comparator.<TrainingPlan>comparingInt(plan -> scoreMap.getOrDefault(plan.getPlanId(), 0)).reversed()
                        .thenComparing(TrainingPlan::getPlanId))
                .limit(4)
                .map(plan -> trainingPlanRecommendation(plan, scoreMap.getOrDefault(plan.getPlanId(), 0),
                        reasonMap.getOrDefault(plan.getPlanId(), "balanced next plan")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> trainingPlanRecommendation(TrainingPlan plan, int score, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("planId", plan.getPlanId());
        item.put("planName", plan.getPlanName());
        item.put("planType", plan.getPlanType());
        item.put("difficultyLevel", plan.getDifficultyLevel());
        item.put("durationDays", plan.getDurationDays());
        item.put("coverUrl", plan.getCoverUrl());
        item.put("score", score);
        item.put("reason", reason);
        return item;
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
