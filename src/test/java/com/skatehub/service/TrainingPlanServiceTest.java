package com.skatehub.service;

import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.TrainingTaskRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.dao.UserTrainingRecordRepository;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.TrainingTask;
import com.skatehub.pojo.skate.TrainingTaskCompleteRequest;
import com.skatehub.pojo.skate.UserTrainingPlan;
import com.skatehub.pojo.skate.UserTrainingRecord;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private TrainingPlanRepository planRepository;
    @Mock private TrainingTaskRepository taskRepository;
    @Mock private UserTrainingPlanRepository userPlanRepository;
    @Mock private UserTrainingRecordRepository recordRepository;
    @Mock private UserGrowthService userGrowthService;

    @InjectMocks
    private TrainingPlanService trainingPlanService;

    @Test
    void completeTaskCreatesRecordUpdatesProgressAndCompletesWholePlan() {
        TrainingTask task = new TrainingTask();
        task.setTaskId(9L);
        task.setPlanId(2L);
        task.setDayNo(1);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task));

        TrainingPlan plan = new TrainingPlan();
        plan.setPlanId(2L);
        plan.setDurationDays(1);
        plan.setStatus("1");
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan));

        UserTrainingPlan userPlan = new UserTrainingPlan();
        userPlan.setUserPlanId(55L);
        userPlan.setUserId(USER_ID);
        userPlan.setPlanId(2L);
        userPlan.setCurrentDay(1);
        userPlan.setCompletedDays(0);
        userPlan.setStatus("IN_PROGRESS");
        when(userPlanRepository.findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(USER_ID, 2L, "IN_PROGRESS"))
                .thenReturn(Optional.of(userPlan));
        when(recordRepository.existsByUserPlanIdAndTaskId(55L, 9L)).thenReturn(false);
        when(recordRepository.save(any(UserTrainingRecord.class))).thenAnswer(invocation -> {
            UserTrainingRecord saved = invocation.getArgument(0);
            saved.setRecordId(66L);
            return saved;
        });
        when(userGrowthService.grantExp(USER_ID, "TRAINING_TASK_COMPLETE", "TRAINING_TASK", 9L, 8)).thenReturn(108);
        when(userGrowthService.grantExp(USER_ID, "TRAINING_PLAN_COMPLETE", "USER_TRAINING_PLAN", 55L, 30)).thenReturn(138);

        TrainingTaskCompleteRequest request = new TrainingTaskCompleteRequest();
        request.setPracticeMinutes(30);
        request.setContent("完成第一天训练");
        request.setMediaUrls("/upload/train.jpg");

        var result = trainingPlanService.completeTask(currentUser(), 9L, request);

        ArgumentCaptor<UserTrainingRecord> captor = ArgumentCaptor.forClass(UserTrainingRecord.class);
        verify(recordRepository).save(captor.capture());
        assertThat(captor.getValue().getUserPlanId()).isEqualTo(55L);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(userPlan.getCompletedDays()).isEqualTo(1);
        assertThat(userPlan.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.get("completedPlan")).isEqualTo(true);
        assertThat(result.get("taskExpAwarded")).isEqualTo(8);
        assertThat(result.get("planExpAwarded")).isEqualTo(30);
        assertThat(result.get("totalExp")).isEqualTo(138);
    }

    @Test
    void completeTaskReturnsNextTaskWhenPlanContinues() {
        TrainingTask first = new TrainingTask();
        first.setTaskId(9L);
        first.setPlanId(2L);
        first.setDayNo(1);
        first.setSortOrder(1);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(first));

        TrainingTask second = new TrainingTask();
        second.setTaskId(10L);
        second.setPlanId(2L);
        second.setDayNo(2);
        second.setSortOrder(1);
        second.setTaskTitle("Balance line practice");

        TrainingPlan plan = new TrainingPlan();
        plan.setPlanId(2L);
        plan.setDurationDays(3);
        plan.setStatus("1");
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
        when(taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(2L))
                .thenReturn(List.of(first, second));

        UserTrainingPlan userPlan = new UserTrainingPlan();
        userPlan.setUserPlanId(55L);
        userPlan.setUserId(USER_ID);
        userPlan.setPlanId(2L);
        userPlan.setCurrentDay(1);
        userPlan.setCompletedDays(0);
        userPlan.setStatus("IN_PROGRESS");
        when(userPlanRepository.findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(USER_ID, 2L, "IN_PROGRESS"))
                .thenReturn(Optional.of(userPlan));
        when(recordRepository.existsByUserPlanIdAndTaskId(55L, 9L)).thenReturn(false);
        when(recordRepository.save(any(UserTrainingRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userGrowthService.grantExp(USER_ID, "TRAINING_TASK_COMPLETE", "TRAINING_TASK", 9L, 8)).thenReturn(108);

        var result = trainingPlanService.completeTask(currentUser(), 9L, new TrainingTaskCompleteRequest());

        assertThat(result.get("completedPlan")).isEqualTo(false);
        assertThat(result.get("nextTask")).isEqualTo(second);
        assertThat(result.get("recommendedNextAction")).isEqualTo("Next: Balance line practice");
    }

    private CurrentUser currentUser() {
        return new CurrentUser(USER_ID, "USER", "skater");
    }
}
