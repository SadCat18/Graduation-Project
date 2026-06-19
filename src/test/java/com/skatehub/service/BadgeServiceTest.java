package com.skatehub.service;

import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.BadgeRepository;
import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserBadgeRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Badge;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserBadge;
import com.skatehub.pojo.growth.BadgeView;
import com.skatehub.util.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    private static final Long USER_ID = 7L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-06-18T02:15:30Z");

    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private DailyCheckinRepository dailyCheckinRepository;
    @Mock private PostRepository postRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private PlaceReviewRepository placeReviewRepository;
    @Mock private UserRepository userRepository;

    private BadgeService service;

    @BeforeEach
    void setUp() {
        service = new BadgeService(
                badgeRepository,
                userBadgeRepository,
                dailyCheckinRepository,
                postRepository,
                activitySignRepository,
                placeReviewRepository,
                userRepository,
                Clock.fixed(NOW, SHANGHAI));
    }

    @ParameterizedTest(name = "{0} unlocks at {1} and stays locked at {2}")
    @MethodSource("badgeRules")
    void eachRuleUnlocksAtThresholdAndRemainsLockedBelow(
            String code, long target, long below, Metric metric) {
        Badge badge = badge(1L, code, 1);
        stubCatalog(List.of(badge), List.of());
        stubMetric(metric, target);

        BadgeView unlocked = service.evaluateAndList(USER_ID).get(0);

        assertTrue(unlocked.unlocked());
        assertEquals(target, unlocked.current());
        assertEquals(target, unlocked.target());

        stubMetric(metric, below);
        BadgeView locked = service.evaluateAndList(USER_ID).get(0);

        assertFalse(locked.unlocked());
        assertEquals(below, locked.current());
        assertEquals(target, locked.target());
        assertNull(locked.unlockTime());
    }

    @Test
    void streakUsesHistoricalMaximumWhileThirtyCheckinsUsesCumulativeCount() {
        Badge streak = badge(1L, "STREAK_7", 1);
        Badge cumulative = badge(2L, "CHECKIN_30", 2);
        stubCatalog(List.of(streak, cumulative), List.of());
        when(dailyCheckinRepository.findMaximumStreakDaysByUserId(USER_ID)).thenReturn(7L);
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(30L);

        List<BadgeView> views = service.evaluateAndList(USER_ID);

        assertTrue(views.get(0).unlocked());
        assertEquals(7L, views.get(0).current());
        assertTrue(views.get(1).unlocked());
        assertEquals(30L, views.get(1).current());
    }

    @Test
    void historicalSevenDayStreakReconcilesAfterLatestStreakReset() {
        stubCatalog(List.of(badge(1L, "STREAK_7", 1)), List.of());
        when(dailyCheckinRepository.findMaximumStreakDaysByUserId(USER_ID)).thenReturn(7L);

        BadgeView view = service.evaluateAndList(USER_ID).get(0);

        assertTrue(view.unlocked());
        assertEquals(7L, view.current());
        verify(dailyCheckinRepository, never()).findTopByUserIdOrderByCheckinDateDesc(USER_ID);
        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    void existingAwardRemainsUnlockedWithoutAnotherInsertWhenProgressDrops() {
        Badge badge = badge(1L, "POST_10", 1);
        LocalDateTime originalTime = LocalDateTime.of(2026, 1, 2, 3, 4);
        UserBadge award = award(1L, originalTime);
        stubCatalog(List.of(badge), List.of(award));
        when(postRepository.countByUserId(USER_ID)).thenReturn(0L);

        BadgeView view = service.evaluateAndList(USER_ID).get(0);

        assertTrue(view.unlocked());
        assertEquals(originalTime, view.unlockTime());
        assertEquals(0L, view.current());
        verify(userBadgeRepository, never()).save(any());
    }

    @Test
    void locksUserBeforeReconcilingExistingAwards() {
        stubCatalog(List.of(), List.of());

        service.evaluateAndList(USER_ID);

        InOrder order = inOrder(userRepository, userBadgeRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(userBadgeRepository).findByUserIdOrderByUnlockTimeDesc(USER_ID);
    }

    @Test
    void newAwardUsesInjectedClock() {
        stubCatalog(List.of(badge(1L, "FIRST_POST", 1)), List.of());
        when(postRepository.countByUserId(USER_ID)).thenReturn(1L);

        BadgeView view = service.evaluateAndList(USER_ID).get(0);

        LocalDateTime expected = LocalDateTime.ofInstant(NOW, SHANGHAI);
        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertEquals(expected, captor.getValue().getUnlockTime());
        assertEquals(expected, view.unlockTime());
    }

    @Test
    void preservesCatalogOrderAndMetadata() {
        Badge second = badge(2L, "FIRST_POST", 20);
        second.setName("Second");
        Badge first = badge(1L, "FIRST_CHECKIN", 10);
        first.setName("First");
        stubCatalog(List.of(first, second), List.of());
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(postRepository.countByUserId(USER_ID)).thenReturn(0L);

        List<BadgeView> views = service.evaluateAndList(USER_ID);

        assertEquals(List.of("FIRST_CHECKIN", "FIRST_POST"),
                views.stream().map(BadgeView::code).toList());
        assertEquals(List.of("First", "Second"), views.stream().map(BadgeView::name).toList());
    }

    @Test
    void unknownCodeNeverAwards() {
        stubCatalog(List.of(badge(99L, "FUTURE_BADGE", 1)), List.of());

        BadgeView view = service.evaluateAndList(USER_ID).get(0);

        assertFalse(view.unlocked());
        assertTrue(view.current() < view.target());
        assertNull(view.unlockTime());
        verify(userBadgeRepository, never()).save(any());
    }

    @Test
    void sharedMetricsAreQueriedAtMostOnce() {
        List<Badge> badges = List.of(
                badge(1L, "FIRST_CHECKIN", 1), badge(2L, "CHECKIN_30", 2),
                badge(3L, "STREAK_7", 3), badge(4L, "FIRST_POST", 4),
                badge(5L, "POST_10", 5), badge(6L, "FIRST_ACTIVITY_CHECKIN", 6),
                badge(7L, "ACTIVITY_CHECKIN_3", 7), badge(8L, "PLACE_REVIEW_3", 8));
        stubCatalog(badges, List.of());
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(dailyCheckinRepository.findMaximumStreakDaysByUserId(USER_ID)).thenReturn(0L);
        when(postRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(activitySignRepository.countByUserIdAndIsCheckin(USER_ID, "1")).thenReturn(0L);
        when(placeReviewRepository.countByUserId(USER_ID)).thenReturn(0L);

        service.evaluateAndList(USER_ID);

        verify(dailyCheckinRepository, times(1)).countByUserId(USER_ID);
        verify(dailyCheckinRepository, times(1)).findMaximumStreakDaysByUserId(USER_ID);
        verify(postRepository, times(1)).countByUserId(USER_ID);
        verify(activitySignRepository, times(1)).countByUserIdAndIsCheckin(USER_ID, "1");
        verify(placeReviewRepository, times(1)).countByUserId(USER_ID);
    }

    @Test
    void missingUserFailsBeforeLoadingCatalogOrAwards() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.evaluateAndList(USER_ID));

        verify(badgeRepository, never()).findByEnabledOrderBySortOrderAsc(any());
        verify(userBadgeRepository, never()).findByUserIdOrderByUnlockTimeDesc(any());
    }

    private static Stream<Arguments> badgeRules() {
        return Stream.of(
                Arguments.of("FIRST_CHECKIN", 1L, 0L, Metric.DAILY_COUNT),
                Arguments.of("STREAK_7", 7L, 6L, Metric.STREAK),
                Arguments.of("CHECKIN_30", 30L, 29L, Metric.DAILY_COUNT),
                Arguments.of("FIRST_POST", 1L, 0L, Metric.POST_COUNT),
                Arguments.of("POST_10", 10L, 9L, Metric.POST_COUNT),
                Arguments.of("FIRST_ACTIVITY_CHECKIN", 1L, 0L, Metric.ACTIVITY_COUNT),
                Arguments.of("ACTIVITY_CHECKIN_3", 3L, 2L, Metric.ACTIVITY_COUNT),
                Arguments.of("PLACE_REVIEW_3", 3L, 2L, Metric.REVIEW_COUNT));
    }

    private void stubCatalog(List<Badge> badges, List<UserBadge> awards) {
        User user = new User();
        user.setUserId(USER_ID);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(badgeRepository.findByEnabledOrderBySortOrderAsc("1")).thenReturn(badges);
        when(userBadgeRepository.findByUserIdOrderByUnlockTimeDesc(USER_ID)).thenReturn(awards);
    }

    private void stubMetric(Metric metric, long value) {
        switch (metric) {
            case DAILY_COUNT -> when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(value);
            case STREAK -> {
                when(dailyCheckinRepository.findMaximumStreakDaysByUserId(USER_ID)).thenReturn(value);
            }
            case POST_COUNT -> when(postRepository.countByUserId(USER_ID)).thenReturn(value);
            case ACTIVITY_COUNT -> when(activitySignRepository.countByUserIdAndIsCheckin(USER_ID, "1"))
                    .thenReturn(value);
            case REVIEW_COUNT -> when(placeReviewRepository.countByUserId(USER_ID)).thenReturn(value);
        }
    }

    private Badge badge(Long id, String code, int sortOrder) {
        Badge badge = new Badge();
        badge.setBadgeId(id);
        badge.setCode(code);
        badge.setName(code);
        badge.setDescription("Description for " + code);
        badge.setRarity("COMMON");
        badge.setIconCode("icon-" + id);
        badge.setSortOrder(sortOrder);
        badge.setEnabled("1");
        return badge;
    }

    private UserBadge award(Long badgeId, LocalDateTime unlockTime) {
        UserBadge award = new UserBadge();
        award.setUserId(USER_ID);
        award.setBadgeId(badgeId);
        award.setUnlockTime(unlockTime);
        return award;
    }

    private enum Metric {
        DAILY_COUNT, STREAK, POST_COUNT, ACTIVITY_COUNT, REVIEW_COUNT
    }
}
