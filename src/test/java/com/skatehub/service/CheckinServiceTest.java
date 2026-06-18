package com.skatehub.service;

import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.DailyCheckin;
import com.skatehub.pojo.User;
import com.skatehub.pojo.growth.CheckinCalendarResponse;
import com.skatehub.pojo.growth.CheckinResponse;
import com.skatehub.pojo.growth.CheckinStatusResponse;
import com.skatehub.util.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final Long USER_ID = 7L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-06-18T02:15:30Z");

    @Mock
    private DailyCheckinRepository dailyCheckinRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserGrowthService userGrowthService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock clock;
    private CheckinService service;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SHANGHAI);
        service = new CheckinService(dailyCheckinRepository, userRepository, userGrowthService, clock, eventPublisher);
        user = new User();
        user.setUserId(USER_ID);
        user.setExp(40);
    }

    @Test
    void firstCheckinGrantsFiveExp() {
        stubLockedUser();
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, LocalDate.of(2026, 6, 18)))
                .thenReturn(Optional.empty());
        when(dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(USER_ID))
                .thenReturn(Optional.empty());
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(userGrowthService.grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 5))
                .thenReturn(45);

        CheckinResponse response = service.checkin(USER_ID);

        assertFalse(response.alreadyCheckedIn());
        assertEquals(LocalDate.of(2026, 6, 18), response.checkinDate());
        assertEquals(1, response.streakDays());
        assertEquals(1L, response.totalDays());
        assertEquals(5, response.baseExp());
        assertEquals(0, response.bonusExp());
        assertEquals(45, response.totalExp());
        verify(userGrowthService).grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 5);
    }

    @Test
    void duplicateCheckinReturnsStoredValuesWithoutSavingOrGranting() {
        stubLockedUser();
        DailyCheckin stored = checkin(LocalDate.of(2026, 6, 18), 7, 5, 20);
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, stored.getCheckinDate()))
                .thenReturn(Optional.of(stored));
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(13L);

        CheckinResponse response = service.checkin(USER_ID);

        assertTrue(response.alreadyCheckedIn());
        assertEquals(7, response.streakDays());
        assertEquals(13L, response.totalDays());
        assertEquals(5, response.baseExp());
        assertEquals(20, response.bonusExp());
        assertEquals(40, response.totalExp());
        verify(dailyCheckinRepository, never()).save(any());
        verify(userGrowthService, never()).grantExp(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void seventhConsecutiveDayGrantsTwentyFiveExp() {
        assertMilestoneAward(6, 7);
    }

    @Test
    void fourteenthConsecutiveDayGrantsTwentyFiveExp() {
        assertMilestoneAward(13, 14);
    }

    @Test
    void missedDayResetsStreakToOne() {
        stubLockedUser();
        LocalDate today = LocalDate.of(2026, 6, 18);
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, today)).thenReturn(Optional.empty());
        when(dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(USER_ID))
                .thenReturn(Optional.of(checkin(today.minusDays(2), 9, 5, 0)));
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(10L);
        when(userGrowthService.grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 5))
                .thenReturn(45);

        CheckinResponse response = service.checkin(USER_ID);

        assertEquals(1, response.streakDays());
        assertEquals(45, response.totalExp());
        verify(userGrowthService).grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 5);
    }

    @Test
    void usesShanghaiDateAtUtcDayBoundary() {
        Clock boundaryClock = Clock.fixed(Instant.parse("2026-06-17T16:30:00Z"), SHANGHAI);
        service = new CheckinService(dailyCheckinRepository, userRepository, userGrowthService, boundaryClock, eventPublisher);
        stubLockedUser();
        LocalDate shanghaiToday = LocalDate.of(2026, 6, 18);
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, shanghaiToday))
                .thenReturn(Optional.of(checkin(shanghaiToday, 1, 5, 0)));
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(1L);

        CheckinResponse response = service.checkin(USER_ID);

        assertEquals(shanghaiToday, response.checkinDate());
        verify(dailyCheckinRepository).findByUserIdAndCheckinDate(USER_ID, shanghaiToday);
    }

    @Test
    void statusReportsTodayAndCurrentTotals() {
        LocalDate today = LocalDate.of(2026, 6, 18);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, today))
                .thenReturn(Optional.of(checkin(today, 4, 5, 0)));
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(12L);

        CheckinStatusResponse response = service.status(USER_ID);

        assertTrue(response.checkedInToday());
        assertEquals(4, response.streakDays());
        assertEquals(12L, response.totalDays());
        assertEquals(40, response.totalExp());
    }

    @Test
    void statusRetainsYesterdayStreakButDropsOlderStreak() {
        LocalDate today = LocalDate.of(2026, 6, 18);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, today)).thenReturn(Optional.empty());
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(3L);
        when(dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(USER_ID))
                .thenReturn(Optional.of(checkin(today.minusDays(1), 3, 5, 0)))
                .thenReturn(Optional.of(checkin(today.minusDays(2), 3, 5, 0)));

        assertEquals(3, service.status(USER_ID).streakDays());
        assertEquals(0, service.status(USER_ID).streakDays());
    }

    @Test
    void calendarReturnsSortedDatesWithinMonth() {
        YearMonth month = YearMonth.of(2026, 6);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dailyCheckinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(
                USER_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(
                        checkin(LocalDate.of(2026, 6, 2), 1, 5, 0),
                        checkin(LocalDate.of(2026, 6, 18), 2, 5, 0)));

        CheckinCalendarResponse response = service.calendar(USER_ID, month);

        assertEquals("2026-06", response.month());
        assertEquals(List.of(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 18)), response.checkedDates());
    }

    @Test
    void rejectsNullCalendarMonth() {
        assertThrows(BizException.class, () -> service.calendar(USER_ID, null));
    }

    @Test
    void missingUserCannotCheckIn() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.checkin(USER_ID));

        verify(dailyCheckinRepository, never()).save(any());
        verify(userGrowthService, never()).grantExp(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void savedCreateTimeUsesInjectedClock() {
        stubLockedUser();
        LocalDate today = LocalDate.of(2026, 6, 18);
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, today)).thenReturn(Optional.empty());
        when(dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(USER_ID)).thenReturn(Optional.empty());
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn(1L);

        service.checkin(USER_ID);

        ArgumentCaptor<DailyCheckin> captor = ArgumentCaptor.forClass(DailyCheckin.class);
        verify(dailyCheckinRepository).save(captor.capture());
        assertEquals(LocalDateTime.ofInstant(NOW, SHANGHAI), captor.getValue().getCreateTime());
    }

    private void assertMilestoneAward(int previousStreak, int expectedStreak) {
        stubLockedUser();
        LocalDate today = LocalDate.of(2026, 6, 18);
        when(dailyCheckinRepository.findByUserIdAndCheckinDate(USER_ID, today)).thenReturn(Optional.empty());
        when(dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(USER_ID))
                .thenReturn(Optional.of(checkin(today.minusDays(1), previousStreak, 5, 0)));
        when(dailyCheckinRepository.countByUserId(USER_ID)).thenReturn((long) expectedStreak);
        when(userGrowthService.grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 25))
                .thenReturn(65);

        CheckinResponse response = service.checkin(USER_ID);

        assertEquals(expectedStreak, response.streakDays());
        assertEquals(5, response.baseExp());
        assertEquals(20, response.bonusExp());
        assertEquals(65, response.totalExp());
        verify(userGrowthService).grantExp(USER_ID, "DAILY_CHECKIN", "DATE", 20260618L, 25);
    }

    private void stubLockedUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    private DailyCheckin checkin(LocalDate date, int streak, int baseExp, int bonusExp) {
        DailyCheckin checkin = new DailyCheckin();
        checkin.setUserId(USER_ID);
        checkin.setCheckinDate(date);
        checkin.setStreakDays(streak);
        checkin.setBaseExp(baseExp);
        checkin.setBonusExp(bonusExp);
        return checkin;
    }
}
