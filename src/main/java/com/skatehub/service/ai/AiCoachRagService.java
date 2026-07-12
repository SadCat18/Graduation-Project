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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiCoachRagService {

    private static final int MAX_CHUNKS = 5;
    private static final int MAX_CONTENT_LENGTH = 220;

    private final SkateTrickRepository skateTrickRepository;
    private final SkateEquipmentRepository skateEquipmentRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingTaskRepository trainingTaskRepository;
    private final VideoRepository videoRepository;
    private final PostRepository postRepository;

    public AiCoachRagContext retrieve(String question) {
        if (!StringUtils.hasText(question)) {
            return AiCoachRagContext.empty();
        }
        String normalizedQuestion = normalize(question);
        List<String> tokens = extractTokens(normalizedQuestion);
        List<AiCoachRagChunk> chunks = new ArrayList<>();

        addSafetyFaq(chunks, normalizedQuestion);
        addTricks(chunks, tokens);
        addTrainingPlans(chunks, tokens);
        addTrainingTasks(chunks, tokens);
        addVideos(chunks, tokens);
        addPosts(chunks, tokens);
        addEquipments(chunks, tokens);

        List<AiCoachRagChunk> selected = chunks.stream()
                .filter(chunk -> chunk.score() > 0)
                .map(chunk -> withMatchSignals(chunk, tokens, normalizedQuestion))
                .sorted(Comparator.comparingInt(AiCoachRagChunk::score).reversed()
                        .thenComparing(AiCoachRagChunk::sourceType)
                        .thenComparing(chunk -> chunk.title() == null ? "" : chunk.title()))
                .limit(MAX_CHUNKS)
                .toList();

        if (selected.isEmpty()) {
            return AiCoachRagContext.empty();
        }
        return new AiCoachRagContext(selected, formatPrompt(selected));
    }

    private void addTricks(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (SkateTrick trick : skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1")) {
            String title = firstNonBlank(trick.getTrickName(), "Skate trick");
            String content = joinText(trick.getCategory(), trick.getDescription());
            int score = score(title, content, tokens, "trick", "动作", "ollie", "滑行", "刹车");
            chunks.add(new AiCoachRagChunk("TRICK", trick.getTrickId(), title, summarize(content),
                    "/skate", boost(score, 8)));
        }
    }

    private void addTrainingPlans(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (TrainingPlan plan : trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")) {
            String title = firstNonBlank(plan.getPlanName(), "Training plan");
            String content = joinText(plan.getPlanType(), plan.getDescription(), levelText(plan.getDifficultyLevel()),
                    daysText(plan.getDurationDays()));
            int score = score(title, content, tokens, "training", "plan", "训练", "计划", "新手", "入门");
            chunks.add(new AiCoachRagChunk("TRAINING_PLAN", plan.getPlanId(), title, summarize(content),
                    "/skate", boost(score, 4)));
        }
    }

    private void addTrainingTasks(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (TrainingTask task : trainingTaskRepository.findAll()) {
            String title = firstNonBlank(task.getTaskTitle(), "Training task");
            String content = joinText(task.getTaskContent(), "第" + valueOrZero(task.getDayNo()) + "天",
                    minutesText(task.getTargetMinutes()), task.getTrickIds());
            int score = score(title, content, tokens, "task", "训练", "练习", "动作", "ollie");
            chunks.add(new AiCoachRagChunk("TRAINING_TASK", task.getTaskId(), title, summarize(content),
                    "/skate", boost(score, 3)));
        }
    }

    private void addVideos(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (Video video : videoRepository.findAllByOrderByCreateTimeDesc()) {
            String title = firstNonBlank(video.getTitle(), "Video");
            String content = firstNonBlank(video.getIntro(), "");
            int score = score(title, content, tokens, "video", "教学", "视频", "分解", "ollie");
            chunks.add(new AiCoachRagChunk("VIDEO", video.getVideoId(), title, summarize(content),
                    "/videos/" + video.getVideoId(), boost(score, 2)));
        }
    }

    private void addPosts(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (Post post : postRepository.findAll()) {
            String title = firstNonBlank(post.getTitle(), "Post");
            String content = joinText(post.getCategory(), post.getContent());
            int score = score(title, content, tokens, "post", "经验", "技巧", "装备", "活动", "ollie");
            chunks.add(new AiCoachRagChunk("POST", post.getPostId(), title, summarize(content),
                    "/community/post/" + post.getPostId(), score));
        }
    }

    private void addEquipments(List<AiCoachRagChunk> chunks, List<String> tokens) {
        for (SkateEquipment equipment : skateEquipmentRepository.findAll()) {
            if (!"1".equals(equipment.getStatus())) {
                continue;
            }
            String title = firstNonBlank(equipment.getEquipmentName(), "Equipment");
            String content = joinText(equipment.getCategory(), equipment.getBrand(), equipment.getSuitableLevel(),
                    equipment.getSpecParams(), equipment.getDescription());
            int score = score(title, content, tokens, "equipment", "gear", "helmet", "protective", "装备", "护具", "头盔", "护腕", "轮子", "板面", "轴承");
            chunks.add(new AiCoachRagChunk("EQUIPMENT", equipment.getEquipmentId(), title, summarize(content),
                    "/skate", boost(score, 3)));
        }
    }

    private void addSafetyFaq(List<AiCoachRagChunk> chunks, String normalizedQuestion) {
        if (!containsAny(normalizedQuestion, "摔", "疼", "痛", "伤", "流血", "肿", "sprain", "injury", "hurt", "pain", "fall")) {
            return;
        }
        chunks.add(new AiCoachRagChunk(
                "FAQ",
                0L,
                "滑板摔倒与疼痛安全提醒",
                "如果摔倒后出现明显疼痛、肿胀、流血、活动受限或症状持续，不要做医学诊断，应停止练习并及时寻求专业医疗帮助。练习时优先佩戴头盔、护腕、护膝和护肘。",
                "",
                100
        ));
    }

    private int score(String title, String content, List<String> tokens, String... boosts) {
        String titleText = normalize(title);
        String bodyText = normalize(content);
        int score = 0;
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (titleText.contains(token)) {
                score += 18;
            }
            if (bodyText.contains(token)) {
                score += 8;
            }
        }
        String merged = titleText + " " + bodyText;
        for (String boost : boosts) {
            String normalizedBoost = normalize(boost);
            if (tokens.contains(normalizedBoost) && merged.contains(normalizedBoost)) {
                score += 4;
            }
        }
        return score;
    }

    private int boost(int score, int sourceBoost) {
        return score <= 0 ? 0 : score + sourceBoost;
    }

    private List<String> extractTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : text.split("[^a-z0-9]+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        addIfPresent(tokens, text, "ollie", "shove", "kickflip", "滑行", "刹车", "转弯", "动作", "技巧", "训练", "计划",
                "练习", "新手", "入门", "装备", "护具", "板", "轮", "鞋", "活动", "比赛", "摔", "疼", "痛", "伤", "肿",
                "gear", "helmet", "protective", "wrist", "bearing", "wheel", "deck");
        return new ArrayList<>(tokens);
    }

    private void addIfPresent(Set<String> tokens, String text, String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (text.contains(normalized)) {
                tokens.add(normalized);
            }
        }
    }

    private String formatPrompt(List<AiCoachRagChunk> chunks) {
        StringBuilder builder = new StringBuilder("Station reference material:\n");
        for (int i = 0; i < chunks.size(); i++) {
            AiCoachRagChunk chunk = chunks.get(i);
            builder.append("[")
                    .append(i + 1)
                    .append("] Type: ")
                    .append(chunk.sourceType())
                    .append("\nTitle: ")
                    .append(chunk.title())
                    .append("\nContent: ")
                    .append(chunk.content())
                    .append("\nSignals: ")
                    .append(String.join(", ", chunk.matchSignals()))
                    .append("\nRoute: ")
                    .append(firstNonBlank(chunk.routePath(), ""))
                    .append("\n\n");
        }
        builder.append("Use these station references when relevant. If references are weak or missing, say the station material is limited. Do not invent SkateHub courses, videos, plans, or events.");
        return builder.toString();
    }

    private String summarize(String value) {
        String text = firstNonBlank(value, "").replaceAll("\\s+", " ").trim();
        if (text.length() <= MAX_CONTENT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_CONTENT_LENGTH) + "...";
    }

    private AiCoachRagChunk withMatchSignals(AiCoachRagChunk chunk, List<String> tokens, String question) {
        Set<String> signals = new LinkedHashSet<>();
        String text = normalize(chunk.title()) + " " + normalize(chunk.content()) + " " + question;
        for (String token : tokens) {
            if (StringUtils.hasText(token) && text.contains(token)) {
                signals.add("keyword:" + token);
            }
        }
        addSemanticSignal(signals, text, "beginner", "beginner", "newbie", "starter", "入门", "新手");
        addSemanticSignal(signals, text, "balance", "balance", "manual", "control", "平衡", "重心");
        addSemanticSignal(signals, text, "protective-gear", "helmet", "protective", "wrist", "knee", "gear", "护具", "头盔", "护腕");
        addSemanticSignal(signals, text, "ollie", "ollie", "pop", "slide", "豚跳");
        addSemanticSignal(signals, text, "safety", "safe", "safety", "hurt", "pain", "injury", "安全", "摔", "疼");
        return new AiCoachRagChunk(
                chunk.sourceType(),
                chunk.sourceId(),
                chunk.title(),
                chunk.content(),
                chunk.routePath(),
                chunk.score(),
                List.copyOf(signals));
    }

    private void addSemanticSignal(Set<String> signals, String text, String signal, String... values) {
        if (containsAny(text, values)) {
            signals.add("semantic:" + signal);
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String levelText(Integer level) {
        return level == null ? "" : "难度 " + level;
    }

    private String daysText(Integer days) {
        return days == null ? "" : days + " 天";
    }

    private String minutesText(Integer minutes) {
        return minutes == null ? "" : minutes + " 分钟";
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
