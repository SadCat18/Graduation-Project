package com.skatehub.service.ai;

import com.skatehub.dao.AiKnowledgeChunkRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.SkateEquipmentRepository;
import com.skatehub.dao.SkateTrickRepository;
import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.TrainingTaskRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.ai.AiKnowledgeChunk;
import com.skatehub.pojo.skate.SkateEquipment;
import com.skatehub.pojo.skate.SkateTrick;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.TrainingTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiKnowledgeChunkSyncService {

    private final AiKnowledgeChunkRepository chunkRepository;
    private final SkateTrickRepository skateTrickRepository;
    private final SkateEquipmentRepository skateEquipmentRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingTaskRepository trainingTaskRepository;
    private final VideoRepository videoRepository;
    private final PostRepository postRepository;

    public int syncPublicKnowledge() {
        List<AiKnowledgeChunk> chunks = new ArrayList<>();
        skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1")
                .forEach(item -> chunks.add(chunk("TRICK", item.getTrickId(), item.getTrickName(), item.getDescription(),
                        item.getCategory(), "/skate", level(item.getDifficultyLevel()))));
        skateEquipmentRepository.findAll().stream()
                .filter(item -> "1".equals(item.getStatus()))
                .forEach(item -> chunks.add(chunk("EQUIPMENT", item.getEquipmentId(), item.getEquipmentName(),
                        join(item.getBrand(), item.getSuitableLevel(), item.getSpecParams(), item.getDescription()),
                        item.getCategory(), "/skate", item.getSuitableLevel())));
        trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")
                .forEach(item -> chunks.add(chunk("TRAINING_PLAN", item.getPlanId(), item.getPlanName(), item.getDescription(),
                        item.getPlanType(), "/skate", level(item.getDifficultyLevel()))));
        trainingTaskRepository.findAll()
                .forEach(item -> chunks.add(chunk("TRAINING_TASK", item.getTaskId(), item.getTaskTitle(), item.getTaskContent(),
                        item.getTrickIds(), "/skate", null)));
        videoRepository.findAllByOrderByCreateTimeDesc()
                .forEach(item -> chunks.add(chunk("VIDEO", item.getVideoId(), item.getTitle(), item.getIntro(),
                        item.getTrickTags(), "/videos/" + item.getVideoId(), null)));
        postRepository.findAll()
                .forEach(item -> chunks.add(chunk("POST", item.getPostId(), item.getTitle(), item.getContent(),
                        item.getCategory(), "/community/post/" + item.getPostId(), null)));
        chunks.add(chunk("FAQ", 0L, "Skate safety checklist",
                "Wear helmet and wrist guards. Check weather, lighting, traffic, hydration, and return plans. For injury, stop skating and seek medical help when symptoms are serious.",
                "safety,injury,protective gear", "/ai-coach", "beginner"));

        chunks.forEach(this::upsert);
        return chunks.size();
    }

    private void upsert(AiKnowledgeChunk incoming) {
        AiKnowledgeChunk chunk = chunkRepository.findBySourceTypeAndSourceId(incoming.getSourceType(), incoming.getSourceId())
                .orElse(incoming);
        chunk.setTitle(incoming.getTitle());
        chunk.setContent(incoming.getContent());
        chunk.setTags(incoming.getTags());
        chunk.setRoutePath(incoming.getRoutePath());
        chunk.setLevelHint(incoming.getLevelHint());
        chunk.setPublicVisible("1");
        chunkRepository.save(chunk);
    }

    private AiKnowledgeChunk chunk(String type, Long id, String title, String content, String tags, String routePath, String levelHint) {
        AiKnowledgeChunk chunk = new AiKnowledgeChunk();
        chunk.setSourceType(type);
        chunk.setSourceId(id == null ? 0L : id);
        chunk.setTitle(StringUtils.hasText(title) ? title.trim() : type);
        chunk.setContent(content);
        chunk.setTags(tags);
        chunk.setRoutePath(routePath);
        chunk.setLevelHint(levelHint);
        return chunk;
    }

    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String level(Integer difficultyLevel) {
        return difficultyLevel == null ? null : "level-" + difficultyLevel;
    }
}
