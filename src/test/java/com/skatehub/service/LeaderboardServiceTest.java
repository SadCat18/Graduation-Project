package com.skatehub.service;

import com.skatehub.dao.LeaderboardQueryRepository;
import com.skatehub.pojo.growth.LeaderboardEntry;
import com.skatehub.pojo.growth.LeaderboardResponse;
import com.skatehub.util.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Long USER_ID = 7L;

    @Mock
    private LeaderboardQueryRepository repository;

    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        service = serviceAt("2026-06-18T02:15:30Z");
    }

    @Test
    void totalDelegatesPaginationAndCurrentUserLookup() {
        LeaderboardEntry first = entry(11, 23L, 900);
        LeaderboardEntry current = entry(17, USER_ID, 700);
        when(repository.findTotal(10, 10)).thenReturn(List.of(first));
        when(repository.countTotal()).thenReturn(41L);
        when(repository.findTotalUser(USER_ID)).thenReturn(Optional.of(current));

        LeaderboardResponse response = service.list("total", 2, 10, USER_ID);

        assertEquals(41L, response.total());
        assertEquals(List.of(first), response.list());
        assertEquals(current, response.currentUser());
        verify(repository).findTotal(10, 10);
        verify(repository).findTotalUser(USER_ID);
    }

    @Test
    void nullPageAndSizeUseValidatorDefaults() {
        when(repository.findTotal(0, 10)).thenReturn(List.of());
        when(repository.countTotal()).thenReturn(0L);

        LeaderboardResponse response = service.list("total", null, null, null);

        assertEquals(0L, response.total());
        assertEquals(List.of(), response.list());
        assertNull(response.currentUser());
        verify(repository).findTotal(0, 10);
    }

    @Test
    void maximumValidatedPaginationProducesSafeOffset() {
        when(repository.findTotal(499_950, 50)).thenReturn(List.of());
        when(repository.countTotal()).thenReturn(0L);

        service.list("total", 10_000, 50, null);

        verify(repository).findTotal(499_950, 50);
    }

    @Test
    void invalidPageAndSizeAreRejectedBeforeRepositoryAccess() {
        assertThrows(BizException.class, () -> service.list("total", 0, 10, null));
        assertThrows(BizException.class, () -> service.list("total", 1, 51, null));
        verifyNoInteractions(repository);
    }

    @Test
    void unknownOrNonExactTypeIsRejected() {
        assertThrows(BizException.class, () -> service.list("month", 1, 10, USER_ID));
        assertThrows(BizException.class, () -> service.list(" total", 1, 10, USER_ID));
        assertThrows(BizException.class, () -> service.list(null, 1, 10, USER_ID));
        verifyNoInteractions(repository);
    }

    @Test
    void sundayUsesCurrentMondayAndNextMondayAsHalfOpenBounds() {
        service = serviceAt("2026-06-21T14:00:00Z"); // Sunday 22:00 in Shanghai
        LocalDateTime start = LocalDateTime.of(2026, 6, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 22, 0, 0);
        when(repository.findWeekly(start, end, 0, 10)).thenReturn(List.of());
        when(repository.countWeekly(start, end)).thenReturn(0L);
        when(repository.findWeeklyUser(USER_ID, start, end)).thenReturn(Optional.empty());

        LeaderboardResponse response = service.list("weekly", 1, 10, USER_ID);

        assertNull(response.currentUser());
        verify(repository).findWeekly(start, end, 0, 10);
        verify(repository).countWeekly(start, end);
        verify(repository).findWeeklyUser(USER_ID, start, end);
    }

    @Test
    void mondayBoundaryStartsANewNaturalWeek() {
        service = serviceAt("2026-06-21T16:00:00Z"); // Monday 00:00 in Shanghai
        LocalDateTime start = LocalDateTime.of(2026, 6, 22, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 29, 0, 0);
        when(repository.findWeekly(start, end, 20, 20)).thenReturn(List.of());
        when(repository.countWeekly(start, end)).thenReturn(0L);

        service.list("weekly", 2, 20, null);

        verify(repository).findWeekly(start, end, 20, 20);
        verify(repository).countWeekly(start, end);
    }

    @Test
    void weeklyUserWithNoGainIsUnranked() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 22, 0, 0);
        when(repository.findWeekly(start, end, 0, 10)).thenReturn(List.of());
        when(repository.countWeekly(start, end)).thenReturn(0L);
        when(repository.findWeeklyUser(USER_ID, start, end)).thenReturn(Optional.empty());

        LeaderboardResponse response = service.list("weekly", 1, 10, USER_ID);

        assertNull(response.currentUser());
    }

    @Test
    void dimensionLeaderboardUsesActionGroup() {
        LeaderboardEntry first = entry(1, 12L, 55);
        List<String> expectedActions = List.of("TRAINING_TASK_COMPLETE", "TRAINING_PLAN_COMPLETE", "TRICK_CHECKIN");
        when(repository.findByActions(expectedActions, 0, 10)).thenReturn(List.of(first));
        when(repository.countByActions(expectedActions)).thenReturn(1L);
        when(repository.findUserByActions(USER_ID, expectedActions)).thenReturn(Optional.empty());

        LeaderboardResponse response = service.list("training", 1, 10, USER_ID);

        assertEquals(1L, response.total());
        assertEquals(List.of(first), response.list());
        assertNull(response.currentUser());
        verify(repository).findByActions(expectedActions, 0, 10);
        verify(repository).findUserByActions(USER_ID, expectedActions);
    }

    private LeaderboardService serviceAt(String instant) {
        return new LeaderboardService(repository, Clock.fixed(Instant.parse(instant), SHANGHAI));
    }

    private LeaderboardEntry entry(long rank, Long userId, int score) {
        return new LeaderboardEntry(rank, userId, "user-" + userId, null, score, 10);
    }
}
