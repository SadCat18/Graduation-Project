package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.activity.ActivityCreateRequest;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivitySignStatus;
import com.skatehub.util.ActivityStatus;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceContentTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageNotifyService messageNotifyService;
    @Mock private UserGrowthService userGrowthService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void createPreservesDetailContentAndActivityDescriptionSeparately() {
        ActivityCreateRequest request = new ActivityCreateRequest();
        request.setTitle("Graduation skate");
        request.setContent("活动亮点\n- 校内集合\n\n注意事项\n- 自带饮用水");
        request.setActivityDesc("毕业前在学校里轻松约滑，留个纪念。");
        request.setActivityType("街式");
        request.setPlace("成都锦城学院");
        request.setActivityTime(LocalDateTime.now().plusDays(1));
        request.setMaxNum(6);
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        activityService.create(new CurrentUser(7L, "skater", "USER"), request);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("活动亮点\n- 校内集合\n\n注意事项\n- 自带饮用水");
        assertThat(saved.getActivityDesc()).isEqualTo("毕业前在学校里轻松约滑，留个纪念。");
    }
    @Test
    void publicDetailReturnsSafetyCompanionForNightActivity() {
        Activity activity = new Activity();
        activity.setActivityId(8L);
        activity.setUserId(7L);
        activity.setTitle("Night plaza session");
        activity.setActivityDesc("Practice pushing and braking after work.");
        activity.setContent("Bring helmet and share location before leaving.");
        activity.setPlace("West Plaza");
        activity.setCity("Shanghai");
        activity.setActivityTime(LocalDateTime.of(2026, 7, 2, 21, 0));
        activity.setReviewStatus("1");
        activity.setActivityStatus("2");
        activity.setStatus("2");
        when(activityRepository.findById(8L)).thenReturn(Optional.of(activity));

        Map<String, Object> detail = activityService.publicDetail(8L);

        Map<String, Object> safety = (Map<String, Object>) detail.get("safetyCompanion");
        assertThat(safety).containsEntry("nightSession", true);
        assertThat((String) safety.get("shareLocationHint")).contains("trusted contact");
        assertThat((String) safety.get("arrivalConfirmationHint")).contains("confirm arrival");
    }

    @Test
    void publicDetailWithUserReturnsCheckinActionForApprovedSign() {
        Activity activity = approvedActivity(11L, LocalDateTime.now().plusHours(2));
        ActivitySign sign = sign(ActivitySignStatus.APPROVED, "0");
        when(activityRepository.findById(11L)).thenReturn(Optional.of(activity));
        when(activitySignRepository.findByActivityIdAndUserId(11L, 9L)).thenReturn(Optional.of(sign));

        Map<String, Object> detail = activityService.publicDetail(11L, 9L);

        assertThat(detail).containsEntry("signed", true);
        assertThat(detail).containsEntry("signStatus", ActivitySignStatus.APPROVED);
        assertThat(detail).containsEntry("participationState", "APPROVED");
        assertThat(detail).containsEntry("canCheckin", true);
        assertThat(detail).containsEntry("canCancelSign", true);
    }

    @Test
    void publicDetailWithCheckedInExpiredActivityReturnsReviewAction() {
        Activity activity = approvedActivity(12L, LocalDateTime.now().minusHours(2));
        activity.setActivityStatus(ActivityStatus.ENDED);
        activity.setStatus(ActivityStatus.ENDED);
        ActivitySign sign = sign(ActivitySignStatus.APPROVED, "1");
        when(activityRepository.findById(12L)).thenReturn(Optional.of(activity));
        when(activitySignRepository.findByActivityIdAndUserId(12L, 9L)).thenReturn(Optional.of(sign));

        Map<String, Object> detail = activityService.publicDetail(12L, 9L);

        assertThat(detail).containsEntry("participationState", "CHECKED_IN");
        assertThat(detail).containsEntry("canReviewAfterEvent", true);
        assertThat((List<Map<String, Object>>) detail.get("postEventActions"))
                .extracting(item -> item.get("key"))
                .contains("WRITE_RECAP_POST");
    }

    private Activity approvedActivity(Long activityId, LocalDateTime activityTime) {
        Activity activity = new Activity();
        activity.setActivityId(activityId);
        activity.setUserId(7L);
        activity.setTitle("Campus skate");
        activity.setActivityDesc("Practice pushing and braking after work.");
        activity.setContent("Bring helmet and share location before leaving.");
        activity.setPlace("West Plaza");
        activity.setActivityTime(activityTime);
        activity.setReviewStatus(ActivityReviewStatus.APPROVED);
        activity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setMaxNum(4);
        activity.setSignNum(1);
        return activity;
    }

    private ActivitySign sign(String signStatus, String isCheckin) {
        ActivitySign sign = new ActivitySign();
        sign.setSignId(5L);
        sign.setActivityId(11L);
        sign.setUserId(9L);
        sign.setSignStatus(signStatus);
        sign.setIsCheckin(isCheckin);
        return sign;
    }
}
