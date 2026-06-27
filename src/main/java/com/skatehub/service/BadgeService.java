package com.skatehub.service;

import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.BadgeRepository;
import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.EventSignupRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.TrickCheckinRepository;
import com.skatehub.dao.UserBadgeRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.pojo.Badge;
import com.skatehub.pojo.UserBadge;
import com.skatehub.pojo.growth.BadgeView;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private static final List<BadgeDefinition> DEFAULT_BADGES = List.of(
            new BadgeDefinition("FIRST_CHECKIN", "初来乍到", "首次每日签到", "COMMON", "first-checkin", 10),
            new BadgeDefinition("STREAK_7", "坚持训练", "连续签到 7 天", "RARE", "streak-seven", 20),
            new BadgeDefinition("CHECKIN_30", "日积月累", "累计签到 30 天", "LEGENDARY", "checkin-thirty", 30),
            new BadgeDefinition("FIRST_POST", "初次发声", "成功发布第一篇帖子", "COMMON", "first-post", 40),
            new BadgeDefinition("POST_10", "内容达人", "累计成功发布 10 篇帖子", "EPIC", "post-ten", 50),
            new BadgeDefinition("FIRST_ACTIVITY_CHECKIN", "初次约板", "首次完成活动现场签到", "COMMON", "first-activity", 60),
            new BadgeDefinition("ACTIVITY_CHECKIN_3", "约板达人", "累计完成 3 次活动现场签到", "RARE", "activity-three", 70),
            new BadgeDefinition("PLACE_REVIEW_3", "场地探索者", "累计成功提交 3 条场地评价", "RARE", "place-explorer", 80),
            new BadgeDefinition("FIRST_TRICK_CHECKIN", "首次动作打卡", "完成第一次滑板动作打卡", "COMMON", "first-trick", 90),
            new BadgeDefinition("FIRST_TRAINING_PLAN", "第一个训练计划", "完成第一个完整训练计划", "RARE", "training-plan", 100),
            new BadgeDefinition("FIRST_SKATE_EVENT", "首次参加赛事", "完成第一次赛事现场签到", "RARE", "skate-event", 110));

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final PostRepository postRepository;
    private final ActivitySignRepository activitySignRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final TrickCheckinRepository trickCheckinRepository;
    private final UserTrainingPlanRepository userTrainingPlanRepository;
    private final EventSignupRepository eventSignupRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<BadgeView> evaluateAndList(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException("User not found"));

        List<Badge> badges = badgeRepository.findByEnabledOrderBySortOrderAsc("1");
        if (badges.isEmpty()) {
            badges = badgeRepository.saveAll(defaultBadgeCatalog());
        }
        Map<Long, UserBadge> awardsByBadgeId = userBadgeRepository
                .findByUserIdOrderByUnlockTimeDesc(userId)
                .stream()
                .collect(Collectors.toMap(UserBadge::getBadgeId, award -> award, (first, ignored) -> first));
        ProgressMetrics metrics = new ProgressMetrics(userId);

        return badges.stream()
                .map(badge -> evaluate(userId, badge, awardsByBadgeId.get(badge.getBadgeId()), metrics))
                .toList();
    }

    private BadgeView evaluate(Long userId, Badge badge, UserBadge existing, ProgressMetrics metrics) {
        Progress progress = progressFor(badge.getCode(), metrics);
        UserBadge award = existing;
        if (award == null && progress.achievable() && progress.current() >= progress.target()) {
            award = new UserBadge();
            award.setUserId(userId);
            award.setBadgeId(badge.getBadgeId());
            award.setUnlockTime(LocalDateTime.now(clock));
            userBadgeRepository.save(award);
        }

        return new BadgeView(
                badge.getCode(),
                badge.getName(),
                badge.getDescription(),
                badge.getRarity(),
                badge.getIconCode(),
                award != null,
                award == null ? null : award.getUnlockTime(),
                progress.current(),
                progress.target());
    }

    private Progress progressFor(String code, ProgressMetrics metrics) {
        return switch (code) {
            case "FIRST_CHECKIN" -> new Progress(metrics.dailyCount(), 1, true);
            case "STREAK_7" -> new Progress(metrics.maximumStreak(), 7, true);
            case "CHECKIN_30" -> new Progress(metrics.dailyCount(), 30, true);
            case "FIRST_POST" -> new Progress(metrics.postCount(), 1, true);
            case "POST_10" -> new Progress(metrics.postCount(), 10, true);
            case "FIRST_ACTIVITY_CHECKIN" -> new Progress(metrics.activityCheckinCount(), 1, true);
            case "ACTIVITY_CHECKIN_3" -> new Progress(metrics.activityCheckinCount(), 3, true);
            case "PLACE_REVIEW_3" -> new Progress(metrics.reviewCount(), 3, true);
            case "FIRST_TRICK_CHECKIN" -> new Progress(metrics.trickCheckinCount(), 1, true);
            case "FIRST_TRAINING_PLAN" -> new Progress(metrics.trainingPlanCompleteCount(), 1, true);
            case "FIRST_SKATE_EVENT" -> new Progress(metrics.skateEventAttendCount(), 1, true);
            default -> new Progress(0, Long.MAX_VALUE, false);
        };
    }

    private List<Badge> defaultBadgeCatalog() {
        return DEFAULT_BADGES.stream()
                .map(BadgeDefinition::toBadge)
                .toList();
    }

    private record BadgeDefinition(
            String code,
            String name,
            String description,
            String rarity,
            String iconCode,
            Integer sortOrder) {

        private Badge toBadge() {
            Badge badge = new Badge();
            badge.setCode(code);
            badge.setName(name);
            badge.setDescription(description);
            badge.setRarity(rarity);
            badge.setIconCode(iconCode);
            badge.setSortOrder(sortOrder);
            badge.setEnabled("1");
            return badge;
        }
    }

    private record Progress(long current, long target, boolean achievable) {
    }

    private final class ProgressMetrics {
        private final Long userId;
        private Long dailyCount;
        private Long maximumStreak;
        private Long postCount;
        private Long activityCheckinCount;
        private Long reviewCount;
        private Long trickCheckinCount;
        private Long trainingPlanCompleteCount;
        private Long skateEventAttendCount;

        private ProgressMetrics(Long userId) {
            this.userId = userId;
        }

        private long dailyCount() {
            dailyCount = cached(dailyCount, () -> dailyCheckinRepository.countByUserId(userId));
            return dailyCount;
        }

        private long maximumStreak() {
            maximumStreak = cached(maximumStreak,
                    () -> dailyCheckinRepository.findMaximumStreakDaysByUserId(userId));
            return maximumStreak;
        }

        private long postCount() {
            postCount = cached(postCount, () -> postRepository.countByUserId(userId));
            return postCount;
        }

        private long activityCheckinCount() {
            activityCheckinCount = cached(activityCheckinCount,
                    () -> activitySignRepository.countByUserIdAndIsCheckin(userId, "1"));
            return activityCheckinCount;
        }

        private long reviewCount() {
            reviewCount = cached(reviewCount, () -> placeReviewRepository.countByUserId(userId));
            return reviewCount;
        }

        private long trickCheckinCount() {
            trickCheckinCount = cached(trickCheckinCount, () -> trickCheckinRepository.countByUserId(userId));
            return trickCheckinCount;
        }

        private long trainingPlanCompleteCount() {
            trainingPlanCompleteCount = cached(trainingPlanCompleteCount,
                    () -> userTrainingPlanRepository.countByUserIdAndStatus(userId, "COMPLETED"));
            return trainingPlanCompleteCount;
        }

        private long skateEventAttendCount() {
            skateEventAttendCount = cached(skateEventAttendCount,
                    () -> eventSignupRepository.countByUserIdAndSignupStatus(userId, "ATTENDED"));
            return skateEventAttendCount;
        }

        private Long cached(Long value, LongSupplier supplier) {
            return value == null ? supplier.getAsLong() : value;
        }
    }
}
