package com.skatehub.service;

import com.skatehub.event.GrowthActionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeAwardListener {

    private final BadgeService badgeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrowthActionCompleted(GrowthActionCompletedEvent event) {
        try {
            badgeService.evaluateAndList(event.userId());
        } catch (RuntimeException exception) {
            log.warn("Badge evaluation failed for user {} after action {}",
                    event.userId(), event.actionType(), exception);
        }
    }
}
