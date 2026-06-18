package com.skatehub.service;

import com.skatehub.event.GrowthActionCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BadgeAwardListenerTest {

    @Test
    void evaluatesBadgesForCompletedAction() {
        BadgeService badgeService = mock(BadgeService.class);
        BadgeAwardListener listener = new BadgeAwardListener(badgeService);

        listener.onGrowthActionCompleted(new GrowthActionCompletedEvent(7L, "POST_CREATE"));

        verify(badgeService).evaluateAndList(7L);
    }

    @Test
    void badgeEvaluationFailureDoesNotEscapeListener() {
        BadgeService badgeService = mock(BadgeService.class);
        doThrow(new RuntimeException("badge storage unavailable"))
                .when(badgeService).evaluateAndList(7L);
        BadgeAwardListener listener = new BadgeAwardListener(badgeService);

        assertDoesNotThrow(() -> listener.onGrowthActionCompleted(
                new GrowthActionCompletedEvent(7L, "DAILY_CHECKIN")));
    }

    @Test
    void listenerRunsAfterCommit() throws Exception {
        Method method = BadgeAwardListener.class.getDeclaredMethod(
                "onGrowthActionCompleted", GrowthActionCompletedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
