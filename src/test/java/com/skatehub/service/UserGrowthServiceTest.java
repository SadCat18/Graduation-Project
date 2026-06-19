package com.skatehub.service;

import com.skatehub.dao.UserGrowthLogRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserGrowthLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrowthServiceTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-06-18T16:00:01Z");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserGrowthLogRepository userGrowthLogRepository;

    private UserGrowthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserGrowthService(userRepository, userGrowthLogRepository, Clock.fixed(NOW, SHANGHAI));
        user = new User();
        user.setUserId(USER_ID);
        user.setExp(40);
    }

    @Test
    void locksUserBeforeIdempotencyLookupAndReturnsUpdatedTotal() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userGrowthLogRepository.findByUserIdAndActionTypeAndTargetTypeAndTargetId(
                USER_ID, "POST_CREATE", "POST", 11L)).thenReturn(Optional.empty());

        int totalExp = service.grantExp(USER_ID, "POST_CREATE", "POST", 11L, 20);

        assertEquals(60, totalExp);
        assertEquals(60, user.getExp());
        InOrder order = inOrder(userRepository, userGrowthLogRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(userGrowthLogRepository)
                .findByUserIdAndActionTypeAndTargetTypeAndTargetId(USER_ID, "POST_CREATE", "POST", 11L);
        order.verify(userGrowthLogRepository).save(any(UserGrowthLog.class));
        order.verify(userRepository).save(user);
    }

    @Test
    void newGrowthLogUsesInjectedClock() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userGrowthLogRepository.findByUserIdAndActionTypeAndTargetTypeAndTargetId(
                USER_ID, "POST_CREATE", "POST", 11L)).thenReturn(Optional.empty());

        service.grantExp(USER_ID, "POST_CREATE", "POST", 11L, 20);

        ArgumentCaptor<UserGrowthLog> captor = ArgumentCaptor.forClass(UserGrowthLog.class);
        verify(userGrowthLogRepository).save(captor.capture());
        assertEquals(LocalDateTime.ofInstant(NOW, SHANGHAI), captor.getValue().getCreateTime());
    }

    @Test
    void duplicateGrantReturnsLockedUsersCurrentTotalWithoutWrites() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userGrowthLogRepository.findByUserIdAndActionTypeAndTargetTypeAndTargetId(
                USER_ID, "POST_LIKE", "POST", 12L)).thenReturn(Optional.of(new UserGrowthLog()));

        int totalExp = service.grantExp(USER_ID, "POST_LIKE", "POST", 12L, 2);

        assertEquals(40, totalExp);
        InOrder order = inOrder(userRepository, userGrowthLogRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(userGrowthLogRepository)
                .findByUserIdAndActionTypeAndTargetTypeAndTargetId(USER_ID, "POST_LIKE", "POST", 12L);
        verify(userGrowthLogRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void missingUserReturnsZeroWithoutCheckingOrWritingGrowthLog() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        int totalExp = service.grantExp(USER_ID, "POST_CREATE", "POST", 11L, 20);

        assertEquals(0, totalExp);
        verifyNoInteractions(userGrowthLogRepository);
        verify(userRepository, never()).save(any());
    }

    @Test
    void invalidUserOrNonPositiveGainReturnsZeroWithoutRepositoryCalls() {
        assertEquals(0, service.grantExp(null, "POST_CREATE", "POST", 11L, 20));
        assertEquals(0, service.grantExp(USER_ID, "POST_CREATE", "POST", 11L, 0));
        assertEquals(0, service.grantExp(USER_ID, "POST_CREATE", "POST", 11L, -1));

        verifyNoInteractions(userRepository, userGrowthLogRepository);
    }
}
