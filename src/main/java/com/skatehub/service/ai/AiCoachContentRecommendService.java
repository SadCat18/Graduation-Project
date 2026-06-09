package com.skatehub.service.ai;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.ai.AiCoachRelatedActivityResponse;
import com.skatehub.pojo.ai.AiCoachRelatedPostResponse;
import com.skatehub.pojo.ai.AiCoachRelatedVideoResponse;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiCoachContentRecommendService {

    private static final int MAX_POSTS = 3;
    private static final int MAX_VIDEOS = 3;
    private static final int MAX_ACTIVITIES = 3;

    private final PostRepository postRepository;
    private final VideoRepository videoRepository;
    private final ActivityRepository activityRepository;

    public List<AiCoachRelatedPostResponse> recommendPosts(String question, String reply) {
        IntentProfile intent = buildIntentProfile(question, reply);
        return postRepository.findAll().stream()
                .map(post -> new ScoredPost(post, scorePost(post, intent)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPost::score).reversed()
                        .thenComparing(item -> item.post().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_POSTS)
                .map(item -> AiCoachRelatedPostResponse.builder()
                        .postId(item.post().getPostId())
                        .title(item.post().getTitle())
                        .category(item.post().getCategory())
                        .content(summarize(item.post().getContent(), 80))
                        .createTime(item.post().getCreateTime())
                        .build())
                .toList();
    }

    public List<AiCoachRelatedVideoResponse> recommendVideos(String question, String reply) {
        IntentProfile intent = buildIntentProfile(question, reply);
        return videoRepository.findAllByOrderByCreateTimeDesc().stream()
                .map(video -> new ScoredVideo(video, scoreVideo(video, intent)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredVideo::score).reversed()
                        .thenComparing(item -> item.video().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_VIDEOS)
                .map(item -> AiCoachRelatedVideoResponse.builder()
                        .videoId(item.video().getVideoId())
                        .title(item.video().getTitle())
                        .intro(summarize(item.video().getIntro(), 80))
                        .cover(item.video().getCover())
                        .url(item.video().getUrl())
                        .createTime(item.video().getCreateTime())
                        .build())
                .toList();
    }

    public List<AiCoachRelatedActivityResponse> recommendActivities(String question, String reply) {
        IntentProfile intent = buildIntentProfile(question, reply);
        return activityRepository.findAll().stream()
                .filter(this::isVisibleActivity)
                .map(activity -> new ScoredActivity(activity, scoreActivity(activity, intent)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredActivity::score).reversed()
                        .thenComparing(item -> item.activity().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ACTIVITIES)
                .map(item -> AiCoachRelatedActivityResponse.builder()
                        .activityId(item.activity().getActivityId())
                        .title(item.activity().getTitle())
                        .city(item.activity().getCity())
                        .district(item.activity().getDistrict())
                        .activityType(item.activity().getActivityType())
                        .activityTime(item.activity().getActivityTime())
                        .build())
                .toList();
    }

    private IntentProfile buildIntentProfile(String question, String reply) {
        String text = normalize(question) + " " + normalize(reply);
        Set<String> keywords = new LinkedHashSet<>();
        addKeywords(keywords, text, "ollie", "滑行", "平衡", "动作", "技巧", "练习", "训练", "落地", "刹车",
                "转体", "新手", "入门", "教学", "板", "轮", "桥", "轴承", "护具", "鞋", "装备", "活动", "约板", "同城", "比赛");

        boolean trickIntent = containsAny(text, "ollie", "动作", "技巧", "练习", "训练", "落地", "平衡", "教学", "入门", "滑行", "刹车");
        boolean gearIntent = containsAny(text, "装备", "板面", "板桥", "轮子", "轮", "轴承", "砂纸", "鞋", "护具", "选板");
        boolean activityIntent = containsAny(text, "活动", "约板", "同城", "比赛", "聚会", "参加", "集合");

        if (trickIntent) {
            addKeywords(keywords, text, "技巧", "教学", "训练", "动作", "经验");
        }
        if (gearIntent) {
            addKeywords(keywords, text, "装备", "板", "鞋", "轮", "桥");
        }
        if (activityIntent) {
            addKeywords(keywords, text, "活动", "同城", "约板", "比赛");
        }

        return new IntentProfile(trickIntent, gearIntent, activityIntent, new ArrayList<>(keywords));
    }

    private int scorePost(Post post, IntentProfile intent) {
        int score = 0;
        String text = normalize(post.getTitle()) + " " + normalize(post.getContent()) + " " + normalize(post.getCategory());
        for (String keyword : intent.keywords()) {
            if (text.contains(keyword)) {
                score += 6;
            }
        }
        if (intent.trickIntent() && containsAny(text, "技巧", "教学", "动作", "练习", "经验", "ollie")) {
            score += 18;
        }
        if (intent.gearIntent() && containsAny(text, "装备", "板", "轮", "桥", "鞋", "护具")) {
            score += 18;
        }
        if (intent.activityIntent() && containsAny(text, "活动", "约板", "同城", "集合")) {
            score += 10;
        }
        if (StringUtils.hasText(post.getCategory())) {
            if (intent.trickIntent() && "技巧交流".equals(post.getCategory().trim())) {
                score += 20;
            }
            if (intent.gearIntent() && "装备讨论".equals(post.getCategory().trim())) {
                score += 20;
            }
            if (intent.activityIntent() && "活动讨论".equals(post.getCategory().trim())) {
                score += 14;
            }
        }
        return score;
    }

    private int scoreVideo(Video video, IntentProfile intent) {
        int score = 0;
        String text = normalize(video.getTitle()) + " " + normalize(video.getIntro());
        for (String keyword : intent.keywords()) {
            if (text.contains(keyword)) {
                score += 6;
            }
        }
        if (intent.trickIntent() && containsAny(text, "教学", "动作", "练习", "ollie", "入门")) {
            score += 22;
        }
        if (intent.gearIntent() && containsAny(text, "装备", "板", "轮", "桥", "鞋")) {
            score += 12;
        }
        if (intent.activityIntent() && containsAny(text, "活动", "比赛", "约板")) {
            score += 8;
        }
        return score;
    }

    private int scoreActivity(Activity activity, IntentProfile intent) {
        int score = 0;
        String text = normalize(activity.getTitle()) + " "
                + normalize(activity.getContent()) + " "
                + normalize(activity.getActivityType()) + " "
                + normalize(activity.getCity()) + " "
                + normalize(activity.getDistrict());
        for (String keyword : intent.keywords()) {
            if (text.contains(keyword)) {
                score += 5;
            }
        }
        if (intent.activityIntent()) {
            score += 20;
        }
        if (containsAny(text, "活动", "约板", "比赛", "刷街", "训练")) {
            score += 10;
        }
        if (intent.trickIntent() && containsAny(text, "训练", "教学", "新手")) {
            score += 8;
        }
        return score;
    }

    private boolean isVisibleActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        if (!ActivityReviewStatus.APPROVED.equals(activity.getReviewStatus())) {
            return false;
        }
        String status = StringUtils.hasText(activity.getActivityStatus()) ? activity.getActivityStatus() : activity.getStatus();
        return ActivityStatus.SIGNUP_OPEN.equals(status)
                || ActivityStatus.FULL.equals(status)
                || ActivityStatus.ENDED.equals(status);
    }

    private void addKeywords(Set<String> keywords, String text, String... values) {
        for (String value : values) {
            String keyword = normalize(value);
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                keywords.add(keyword);
            }
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            String keyword = normalize(value);
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String summarize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }

    private record IntentProfile(boolean trickIntent,
                                 boolean gearIntent,
                                 boolean activityIntent,
                                 List<String> keywords) {
    }

    private record ScoredPost(Post post, int score) {
    }

    private record ScoredVideo(Video video, int score) {
    }

    private record ScoredActivity(Activity activity, int score) {
    }
}
