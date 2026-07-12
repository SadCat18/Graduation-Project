package com.skatehub.service.ai;

import com.skatehub.dao.PostRepository;
import com.skatehub.dao.SkateEquipmentRepository;
import com.skatehub.dao.SkateTrickRepository;
import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.TrainingTaskRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.skate.SkateEquipment;
import com.skatehub.pojo.skate.SkateTrick;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.TrainingTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCoachRagServiceTest {

    @Mock private SkateTrickRepository skateTrickRepository;
    @Mock private SkateEquipmentRepository skateEquipmentRepository;
    @Mock private TrainingPlanRepository trainingPlanRepository;
    @Mock private TrainingTaskRepository trainingTaskRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private PostRepository postRepository;

    private AiCoachRagService service;

    @BeforeEach
    void setUp() {
        service = new AiCoachRagService(
                skateTrickRepository,
                skateEquipmentRepository,
                trainingPlanRepository,
                trainingTaskRepository,
                videoRepository,
                postRepository
        );
        lenient().when(skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1")).thenReturn(List.of());
        lenient().when(skateEquipmentRepository.findAll()).thenReturn(List.of());
        lenient().when(trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")).thenReturn(List.of());
        lenient().when(trainingTaskRepository.findAll()).thenReturn(List.of());
        lenient().when(videoRepository.findAllByOrderByCreateTimeDesc()).thenReturn(List.of());
        lenient().when(postRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void retrievesMatchingTrickAsPromptReference() {
        SkateTrick trick = new SkateTrick();
        trick.setTrickId(7L);
        trick.setTrickName("Ollie 基础练习");
        trick.setCategory("入门动作");
        trick.setDescription("后脚点板，前脚刷板，保持重心在板上方。");
        when(skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1")).thenReturn(List.of(trick));

        AiCoachRagContext context = service.retrieve("Ollie 总是跳不起来怎么办");

        assertThat(context.chunks()).hasSize(1);
        assertThat(context.chunks().get(0).sourceType()).isEqualTo("TRICK");
        assertThat(context.chunks().get(0).sourceId()).isEqualTo(7L);
        assertThat(context.chunks().get(0).routePath()).isEqualTo("/skate");
        assertThat(context.promptText())
                .contains("Station reference material")
                .contains("Ollie 基础练习")
                .contains("/skate");
    }

    @Test
    void retrievesSafetyReferenceForInjuryQuestion() {
        AiCoachRagContext context = service.retrieve("练习摔倒后手腕疼还肿了怎么办");

        assertThat(context.chunks()).isNotEmpty();
        assertThat(context.chunks().get(0).sourceType()).isEqualTo("FAQ");
        assertThat(context.promptText()).contains("不要做医学诊断");
    }

    @Test
    void returnsEmptyContextForBlankQuestion() {
        AiCoachRagContext context = service.retrieve("   ");

        assertThat(context.chunks()).isEmpty();
        assertThat(context.promptText()).isBlank();
    }

    @Test
    void returnsEmptyContextWhenNoKnowledgeMatches() {
        SkateTrick trick = new SkateTrick();
        trick.setTrickId(7L);
        trick.setTrickName("Ollie");
        trick.setDescription("Pop and slide the front foot.");
        when(skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1")).thenReturn(List.of(trick));

        AiCoachRagContext context = service.retrieve("How do I cook pasta?");

        assertThat(context.chunks()).isEmpty();
        assertThat(context.promptText()).isBlank();
    }

    @Test
    void retrievesEquipmentReferenceForGearQuestion() {
        SkateEquipment equipment = new SkateEquipment();
        equipment.setEquipmentId(9L);
        equipment.setEquipmentName("Beginner helmet and wrist guard set");
        equipment.setCategory("protective gear");
        equipment.setSuitableLevel("beginner");
        equipment.setSpecParams("helmet, wrist guard, knee pad");
        equipment.setDescription("Protective gear for new skaters practicing falls and basic balance.");
        when(skateEquipmentRepository.findAll()).thenReturn(List.of(equipment));

        AiCoachRagContext context = service.retrieve("What protective gear should a beginner wear?");

        assertThat(context.chunks()).hasSize(1);
        assertThat(context.chunks().get(0).sourceType()).isEqualTo("EQUIPMENT");
        assertThat(context.chunks().get(0).sourceId()).isEqualTo(9L);
        assertThat(context.chunks().get(0).routePath()).isEqualTo("/skate");
        assertThat(context.chunks().get(0).matchSignals()).contains("semantic:protective-gear");
    }

    @Test
    void hybridRetrievalExposesKeywordAndSemanticSignals() {
        TrainingPlan plan = new TrainingPlan();
        plan.setPlanId(11L);
        plan.setPlanName("Beginner pop balance plan");
        plan.setPlanType("beginner");
        plan.setDescription("Build board control before ollie practice.");
        when(trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")).thenReturn(List.of(plan));

        AiCoachRagContext context = service.retrieve("ollie pop balance for beginner");

        assertThat(context.chunks()).isNotEmpty();
        assertThat(context.chunks().get(0).matchSignals())
                .contains("keyword:ollie", "semantic:beginner", "semantic:balance");
        assertThat(context.promptText()).contains("Signals:");
    }

    @Test
    void limitsReferencesToFiveChunks() {
        TrainingPlan plan = new TrainingPlan();
        plan.setPlanId(1L);
        plan.setPlanName("Ollie 入门训练计划");
        plan.setPlanType("入门");
        plan.setDescription("每天练习 Ollie 起跳和平衡。");

        TrainingTask task = new TrainingTask();
        task.setTaskId(2L);
        task.setPlanId(1L);
        task.setTaskTitle("Ollie 点板训练");
        task.setTaskContent("重复练习点板和刷板。");

        Video video = new Video();
        video.setVideoId(3L);
        video.setTitle("Ollie 教学视频");
        video.setIntro("分解 Ollie 起跳动作。");

        Post post = new Post();
        post.setPostId(4L);
        post.setTitle("Ollie 常见错误");
        post.setCategory("技巧交流");
        post.setContent("很多新手起跳时重心后仰。");

        when(trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")).thenReturn(List.of(plan, plan));
        when(trainingTaskRepository.findAll()).thenReturn(List.of(task, task));
        when(videoRepository.findAllByOrderByCreateTimeDesc()).thenReturn(List.of(video, video));
        when(postRepository.findAll()).thenReturn(List.of(post, post));

        AiCoachRagContext context = service.retrieve("Ollie 新手训练");

        assertThat(context.chunks()).hasSize(5);
    }
}
