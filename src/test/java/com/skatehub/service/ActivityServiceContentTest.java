package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.activity.ActivityCreateRequest;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

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
}
