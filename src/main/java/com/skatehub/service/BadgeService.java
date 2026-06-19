package com.skatehub.service;

import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.BadgeRepository;
import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserBadgeRepository;
import com.skatehub.dao.UserRepository;
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

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final PostRepository postRepository;
    private final ActivitySignRepository activitySignRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<BadgeView> evaluateAndList(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException("User not found"));

        List<Badge> badges = badgeRepository.findByEnabledOrderBySortOrderAsc("1");
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
            default -> new Progress(0, Long.MAX_VALUE, false);
        };
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

        private Long cached(Long value, LongSupplier supplier) {
            return value == null ? supplier.getAsLong() : value;
        }
    }
}
