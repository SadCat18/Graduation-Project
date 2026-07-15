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

    private static final List<String> RECOMMENDABLE_ACTIVITY_STATUSES = List.of(
            ActivityStatus.SIGNUP_OPEN,
            ActivityStatus.FULL,
            ActivityStatus.CANCELED,
            ActivityStatus.ENDED
    );

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

        List<Post> visiblePosts = postRepository
                .findTop100ByOrderByIsTopDescLikeCountDescCollectCountDescCreateTimeDesc();
        List<Post> recommendedPosts = scorePosts(visiblePosts, categories, style).stream()
                .limit(6)
                .collect(Collectors.toList());
        List<Map<String, Object>> postList = mapRecommendedPosts(recommendedPosts);

        List<Activity> visibleActivities = activityRepository
                .findTop80ByReviewStatusAndActivityStatusInOrderByCreateTimeDesc(
                        ActivityReviewStatus.APPROVED,
                        RECOMMENDABLE_ACTIVITY_STATUSES
                );
        List<Activity> recommendedActivities = scoreActivities(visibleActivities, cities, style).stream()
                .limit(4)
                .collect(Collectors.toList());
        List<Map<String, Object>> activityList = mapRecommendedActivities(recommendedActivities, userId);

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
        List<Interaction> interactions = new ArrayList<>(interactionRepository
                .findTop50ByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "LIKE"));
        interactions.addAll(interactionRepository
                .findTop50ByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "COLLECT"));
        Set<Long> postIds = interactions.stream()
                .map(Interaction::getTargetId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        commentRepository.findTop50ByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(Comment::getPostId)
                .filter(id -> id != null && id > 0)
                .forEach(postIds::add);
        if (!postIds.isEmpty()) {
            postRepository.findAllById(postIds).stream()
                    .map(Post::getCategory)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(categories::add);
        }
        return categories;
    }

    private Set<String> collectCities(Long userId) {
        Set<String> cities = new HashSet<>();
        List<ActivitySign> signs = activitySignRepository.findTop50ByUserIdOrderBySignTimeDesc(userId);
        Set<Long> signedActivityIds = signs.stream()
                .map(ActivitySign::getActivityId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (!signedActivityIds.isEmpty()) {
            activityRepository.findAllById(signedActivityIds).stream()
                    .map(Activity::getCity)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(cities::add);
        }
        List<Activity> published = activityRepository.findTop20ByUserIdOrderByCreateTimeDesc(userId);
        for (Activity activity : published) {
            if (StringUtils.hasText(activity.getCity())) cities.add(activity.getCity().trim());
        }
        return cities;
    }

    private List<Map<String, Object>> mapRecommendedPosts(List<Post> posts) {
        if (posts.isEmpty()) return List.of();
        List<Long> postIds = posts.stream().map(Post::getPostId).toList();
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countByPostIds(postIds)) {
            if (row.length >= 2 && row[0] instanceof Number postId && row[1] instanceof Number count) {
                commentCounts.put(postId.longValue(), count.longValue());
            }
        }
        Map<Long, User> authors = usersById(posts.stream().map(Post::getUserId).toList());
        return posts.stream().map(post -> {
            User author = authors.get(post.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("postId", post.getPostId());
            item.put("userId", post.getUserId());
            item.put("title", post.getTitle());
            item.put("content", post.getContent());
            item.put("images", post.getImages());
            item.put("category", post.getCategory());
            item.put("likeCount", post.getLikeCount());
            item.put("collectCount", post.getCollectCount());
            item.put("isTop", post.getIsTop());
            item.put("createTime", post.getCreateTime());
            item.put("commentCount", commentCounts.getOrDefault(post.getPostId(), 0L));
            item.put("authorName", author == null ? "已注销用户" : author.getUsername());
            item.put("authorAvatar", author == null ? null : author.getAvatar());
            item.put("authorLevel", author == null || author.getLevel() == null ? 1 : author.getLevel());
            return item;
        }).toList();
    }

    private List<Map<String, Object>> mapRecommendedActivities(List<Activity> activities, Long userId) {
        if (activities.isEmpty()) return List.of();
        List<Long> activityIds = activities.stream().map(Activity::getActivityId).toList();
        Map<Long, User> publishers = usersById(activities.stream().map(Activity::getUserId).toList());
        Map<Long, ActivitySign> signs = activitySignRepository
                .findByUserIdAndActivityIdIn(userId, activityIds).stream()
                .collect(Collectors.toMap(ActivitySign::getActivityId, sign -> sign, (first, ignored) -> first));
        return activities.stream().map(activity -> {
            User publisher = publishers.get(activity.getUserId());
            ActivitySign sign = signs.get(activity.getActivityId());
            String activityStatus = activity.getActivityStatus();
            String signStatus = sign == null ? null : sign.getSignStatus();
            boolean signed = ActivitySignStatus.PENDING_CONFIRM.equals(signStatus)
                    || ActivitySignStatus.APPROVED.equals(signStatus);
            int signNum = safe(activity.getSignNum());
            Integer maxNum = activity.getMaxNum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", activity.getActivityId());
            item.put("userId", activity.getUserId());
            item.put("title", activity.getTitle());
            item.put("content", activity.getContent());
            item.put("activityDesc", StringUtils.hasText(activity.getActivityDesc())
                    ? activity.getActivityDesc() : activity.getContent());
            item.put("place", activity.getPlace());
            item.put("placeId", activity.getPlaceId());
            item.put("address", activity.getAddress());
            item.put("city", activity.getCity());
            item.put("district", activity.getDistrict());
            item.put("longitude", activity.getLongitude());
            item.put("latitude", activity.getLatitude());
            item.put("activityTime", activity.getActivityTime());
            item.put("maxNum", maxNum);
            item.put("signNum", signNum);
            item.put("activityType", activity.getActivityType());
            item.put("reviewStatus", activity.getReviewStatus());
            item.put("activityStatus", activityStatus);
            item.put("status", activityStatus);
            item.put("spotsLeft", maxNum == null ? null : Math.max(0, maxNum - signNum));
            item.put("full", ActivityStatus.FULL.equals(activityStatus)
                    || (maxNum != null && signNum >= maxNum));
            item.put("createTime", activity.getCreateTime());
            item.put("publisherName", publisher == null ? "已注销用户" : publisher.getUsername());
            item.put("signed", signed);
            item.put("signId", sign == null ? null : sign.getSignId());
            item.put("signStatus", signStatus);
            item.put("isCheckin", sign == null ? "0" : sign.getIsCheckin());
            item.put("arrivalConfirmed", sign == null ? "0" : sign.getArrivalConfirmed());
            item.put("returnConfirmed", sign == null ? "0" : sign.getReturnConfirmed());
            return item;
        }).toList();
    }

    private Map<Long, User> usersById(List<Long> userIds) {
        List<Long> distinctUserIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (distinctUserIds.isEmpty()) return Map.of();
        return userRepository.findAllById(distinctUserIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));
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
