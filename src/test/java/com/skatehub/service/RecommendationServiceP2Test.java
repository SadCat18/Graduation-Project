package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.dao.UserTrainingRecordRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.UserTrainingPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceP2Test {

    @Test
    void recommendsTrainingPlansFromLevelAndHistory() {
        UserRepository userRepository = mock(UserRepository.class);
        TrainingPlanRepository trainingPlanRepository = mock(TrainingPlanRepository.class);
        UserTrainingPlanRepository userTrainingPlanRepository = mock(UserTrainingPlanRepository.class);
        UserTrainingRecordRepository userTrainingRecordRepository = mock(UserTrainingRecordRepository.class);
        RecommendationService service = new RecommendationService(
                userRepository,
                mock(PostRepository.class),
                mock(ActivityRepository.class),
                mock(CommunityBulletinRepository.class),
                mock(InteractionRepository.class),
                mock(CommentRepository.class),
                mock(ActivitySignRepository.class),
                mock(PostService.class),
                mock(ActivityService.class),
                mock(CommunityBulletinService.class),
                trainingPlanRepository,
                userTrainingPlanRepository,
                userTrainingRecordRepository
        );
        User user = new User();
        user.setUserId(7L);
        user.setLevel(2);
        user.setSkateStyle("street");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        TrainingPlan beginner = plan(1L, "Beginner street balance", "street", 1, "balance and pushing");
        TrainingPlan advanced = plan(2L, "Advanced rail line", "street", 5, "rail and stairs");
        when(trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")).thenReturn(List.of(advanced, beginner));
        UserTrainingPlan completed = new UserTrainingPlan();
        completed.setPlanId(2L);
        completed.setStatus("COMPLETED");
        when(userTrainingPlanRepository.findByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of(completed));
        when(userTrainingRecordRepository.findByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of());

        Map<String, Object> result = service.recommendForUser(7L);

        List<Map<String, Object>> plans = (List<Map<String, Object>>) result.get("trainingPlans");
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0)).containsEntry("planId", 1L);
        assertThat((String) plans.get(0).get("reason")).contains("level", "style");
    }

    private TrainingPlan plan(Long id, String name, String type, Integer level, String description) {
        TrainingPlan plan = new TrainingPlan();
        plan.setPlanId(id);
        plan.setPlanName(name);
        plan.setPlanType(type);
        plan.setDifficultyLevel(level);
        plan.setDurationDays(7);
        plan.setDescription(description);
        plan.setStatus("1");
        return plan;
    }
}
