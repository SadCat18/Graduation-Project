package com.skatehub.service;

import com.skatehub.dao.EventSignupRepository;
import com.skatehub.dao.SkateEventRepository;
import com.skatehub.pojo.skate.EventSignup;
import com.skatehub.pojo.skate.SkateEvent;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkateEventServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private SkateEventRepository eventRepository;
    @Mock private EventSignupRepository signupRepository;
    @Mock private UserGrowthService userGrowthService;

    @InjectMocks
    private SkateEventService skateEventService;

    @Test
    void signupRejectsFullEvent() {
        SkateEvent event = openEvent();
        event.setMaxParticipants(1);
        when(eventRepository.findByEventIdAndStatus(8L, "SIGNUP_OPEN")).thenReturn(Optional.of(event));
        when(signupRepository.countByEventIdAndSignupStatus(8L, "SIGNED")).thenReturn(1L);

        assertThatThrownBy(() -> skateEventService.signup(currentUser(), 8L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void signupCreatesRecordAndGrantsExperience() {
        SkateEvent event = openEvent();
        when(eventRepository.findByEventIdAndStatus(8L, "SIGNUP_OPEN")).thenReturn(Optional.of(event));
        when(signupRepository.countByEventIdAndSignupStatus(8L, "SIGNED")).thenReturn(0L);
        when(signupRepository.findByEventIdAndUserId(8L, USER_ID)).thenReturn(Optional.empty());
        when(signupRepository.save(any(EventSignup.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userGrowthService.grantExp(USER_ID, "SKATE_EVENT_SIGNUP", "SKATE_EVENT", 8L, 5)).thenReturn(105);

        var result = skateEventService.signup(currentUser(), 8L);

        verify(signupRepository).save(any(EventSignup.class));
        assertThat(result.get("signupStatus")).isEqualTo("SIGNED");
        assertThat(result.get("expAwarded")).isEqualTo(5);
        assertThat(result.get("totalExp")).isEqualTo(105);
    }

    private SkateEvent openEvent() {
        SkateEvent event = new SkateEvent();
        event.setEventId(8L);
        event.setEventTitle("Campus Jam");
        event.setStatus("SIGNUP_OPEN");
        event.setSignupStartTime(LocalDateTime.now().minusDays(1));
        event.setSignupEndTime(LocalDateTime.now().plusDays(1));
        event.setMaxParticipants(20);
        return event;
    }

    private CurrentUser currentUser() {
        return new CurrentUser(USER_ID, "USER", "skater");
    }
}
