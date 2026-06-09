package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivityStatus;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceExpiryTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageNotifyService messageNotifyService;
    @Mock private UserGrowthService userGrowthService;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void signRejectsExpiredActivityEvenWhenStatusIsSignupOpen() {
        Activity activity = approvedActivity(10L, LocalDateTime.now().minusHours(1));
        when(activityRepository.findById(10L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.sign(currentUser(), 10L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void cancelMySignRejectsExpiredActivityEvenWhenStatusIsSignupOpen() {
        Activity activity = approvedActivity(10L, LocalDateTime.now().minusHours(1));
        when(activityRepository.findById(10L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.cancelMySign(currentUser(), 10L))
                .isInstanceOf(BizException.class);
    }

    private Activity approvedActivity(Long activityId, LocalDateTime activityTime) {
        Activity activity = new Activity();
        activity.setActivityId(activityId);
        activity.setUserId(7L);
        activity.setTitle("Campus skate");
        activity.setActivityTime(activityTime);
        activity.setReviewStatus(ActivityReviewStatus.APPROVED);
        activity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setMaxNum(4);
        activity.setSignNum(0);
        return activity;
    }

    private CurrentUser currentUser() {
        return new CurrentUser(7L, "skater", "USER");
    }
}
