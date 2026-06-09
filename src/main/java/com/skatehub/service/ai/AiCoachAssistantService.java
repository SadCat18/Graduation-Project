package com.skatehub.service.ai;

import com.skatehub.dao.AiCoachMessageRepository;
import com.skatehub.dao.AiCoachSessionRepository;
import com.skatehub.pojo.AiCoachMessage;
import com.skatehub.pojo.AiCoachSession;
import com.skatehub.pojo.ai.AiCoachChatRequest;
import com.skatehub.pojo.ai.AiCoachChatResponse;
import com.skatehub.pojo.ai.AiCoachContentRecommendResponse;
import com.skatehub.pojo.ai.AiCoachMessageItemResponse;
import com.skatehub.pojo.ai.AiCoachSessionItemResponse;
import com.skatehub.pojo.ai.AiCoachSessionMessagesResponse;
import com.skatehub.pojo.ai.AiMessage;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.skatehub.util.AiUtils;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCoachAssistantService {

    private static final int MAX_SUGGESTIONS = 4;
    private static final int MAX_CONTEXT_MESSAGES = 6;
    private static final int SESSION_TITLE_MAX_LENGTH = 20;
    private static final String DEFAULT_SESSION_TITLE = "AI滑板老师对话";

    private final AiGatewayService aiGatewayService;
    private final AiCoachContentRecommendService aiCoachContentRecommendService;
    private final AiCoachSessionRepository aiCoachSessionRepository;
    private final AiCoachMessageRepository aiCoachMessageRepository;

    @Transactional
    public AiCoachChatResponse chat(CurrentUser currentUser, AiCoachChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new BizException("提问内容不能为空");
        }

        AiCoachSession session = resolveSession(currentUser, request);
        AiResponse response;
        try {
            response = aiGatewayService.chat(buildAiRequest(currentUser, request, session));
        } catch (BizException exception) {
            throw new BizException("AI 滑板老师调用失败: " + exception.getMessage());
        } catch (Exception exception) {
            throw new BizException("AI 滑板老师调用失败");
        }

        if (response == null || !StringUtils.hasText(response.getContent())) {
            throw new BizException("AI 滑板老师未返回有效内容");
        }

        AiCoachChatResponse result = parseResponse(response.getContent());
        normalizeResult(result, request);
        // fillRelatedContent is now loaded async by frontend
        // fillRelatedContent(result, request);
        saveMessages(session.getSessionId(), request.getMessage().trim(), result.getReply());
        session.setUpdateTime(LocalDateTime.now());
        aiCoachSessionRepository.save(session);
        result.setSessionId(session.getSessionId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<AiCoachSessionItemResponse> listSessions(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        return aiCoachSessionRepository.findByUserIdOrderByUpdateTimeDesc(userId).stream()
                .map(session -> AiCoachSessionItemResponse.builder()
                        .sessionId(session.getSessionId())
                        .title(session.getTitle())
                        .updateTime(session.getUpdateTime())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AiCoachSessionMessagesResponse getSessionMessages(CurrentUser currentUser, String sessionId) {
        AiCoachSession session = getOwnedSession(currentUser, sessionId);
        List<AiCoachMessageItemResponse> messages = aiCoachMessageRepository
                .findBySessionIdOrderByCreateTimeAscMsgIdAsc(session.getSessionId())
                .stream()
                .map(message -> AiCoachMessageItemResponse.builder()
                        .role(message.getRole())
                        .content(message.getContent())
                        .createTime(message.getCreateTime())
                        .build())
                .toList();

        return AiCoachSessionMessagesResponse.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .messages(messages)
                .build();
    }

    @Transactional
    public void deleteSession(CurrentUser currentUser, String sessionId) {
        AiCoachSession session = getOwnedSession(currentUser, sessionId);
        aiCoachMessageRepository.deleteBySessionId(session.getSessionId());
        aiCoachSessionRepository.delete(session);
    }

    private AiRequest buildAiRequest(CurrentUser currentUser,
                                     AiCoachChatRequest request,
                                     AiCoachSession session) {
        return AiRequest.builder()
                .scene("coach_chat")
                .systemPrompt(buildSystemPrompt())
                .messages(buildConversationMessages(currentUser, request, session))
                .temperature(0.5D)
                .maxTokens(600)
                .responseFormat("json_object")
                .build();
    }

    private String buildSystemPrompt() {
        return """
                You are the AI skateboard coach inside a skateboard community website.
                Your job is to answer as a practical, friendly, teaching-oriented coach for logged-in users.
                Prefer step-by-step guidance, explain clearly, and keep advice actionable for real practice.
                When discussing difficult tricks, falls, pain, injuries, or recovery, stay cautious.
                Do not make medical diagnoses. Encourage professional medical help when symptoms may be serious.
                Focus on basic skill learning, safety, body posture, balance, practice sequencing, and confidence building.
                Keep replies concise and actionable. Avoid long explanations.
                You must return valid JSON only, with this shape:
                {
                  "reply": "string",
                  "suggestions": ["string", "string"]
                }
                Rules:
                - reply must be useful for skateboard teaching scenarios.
                - suggestions should contain 2 to 4 short items, each written as a natural user question or request that extends the current topic. For example, write âè½è¯¦ç»è®²è®²è©é¨åååâ instead of âç»ä¹ æ¶æ³¨æè©é¨ååâ.
                - suggestions should be specific, contextual follow-ups to your reply, so the next round of conversation stays meaningful and on-topic.
                - Do not include markdown code fences.
                """;
    }

    private List<AiMessage> buildConversationMessages(CurrentUser currentUser,
                                                      AiCoachChatRequest request,
                                                      AiCoachSession session) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.builder()
                .role("user")
                .content(buildSessionIntro(currentUser, session))
                .build());

        List<AiCoachMessage> history = loadRecentContextMessages(session.getSessionId());
        for (AiCoachMessage item : history) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            messages.add(AiMessage.builder()
                    .role(normalizeRole(item.getRole()))
                    .content(item.getContent().trim())
                    .build());
        }

        messages.add(AiMessage.builder()
                .role("user")
                .content(request.getMessage().trim())
                .build());
        return messages;
    }

    private List<AiCoachMessage> loadRecentContextMessages(String sessionId) {
        List<AiCoachMessage> history = aiCoachMessageRepository
                .findTop12BySessionIdOrderByCreateTimeDescMsgIdDesc(sessionId);
        List<AiCoachMessage> ordered = new ArrayList<>(history);
        ordered.sort((left, right) -> {
            LocalDateTime leftTime = left == null ? null : left.getCreateTime();
            LocalDateTime rightTime = right == null ? null : right.getCreateTime();
            if (leftTime == null && rightTime == null) {
                return compareMsgId(left, right);
            }
            if (leftTime == null) {
                return -1;
            }
            if (rightTime == null) {
                return 1;
            }
            int timeCompare = leftTime.compareTo(rightTime);
            return timeCompare != 0 ? timeCompare : compareMsgId(left, right);
        });
        return ordered.size() <= MAX_CONTEXT_MESSAGES
                ? ordered
                : ordered.subList(Math.max(ordered.size() - MAX_CONTEXT_MESSAGES, 0), ordered.size());
    }

    private int compareMsgId(AiCoachMessage left, AiCoachMessage right) {
        long leftId = left == null || left.getMsgId() == null ? Long.MIN_VALUE : left.getMsgId();
        long rightId = right == null || right.getMsgId() == null ? Long.MIN_VALUE : right.getMsgId();
        return Long.compare(leftId, rightId);
    }

    private String buildSessionIntro(CurrentUser currentUser, AiCoachSession session) {
        String userName = currentUser == null || !StringUtils.hasText(currentUser.name())
                ? "unknown"
                : currentUser.name().trim();
        return "User name: " + userName
                + "\nSession ID: " + session.getSessionId()
                + "\nSession title: " + session.getTitle();
    }

    private String normalizeRole(String role) {
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    private AiCoachChatResponse parseResponse(String content) {
        try {
            return AiUtils.parseJsonQuietly(content, AiCoachChatResponse.class);
        } catch (JsonProcessingException exception) {
            return AiCoachChatResponse.builder()
                    .reply(content.trim())
                    .suggestions(new ArrayList<>())
                    .build();
        }
    }

    private void normalizeResult(AiCoachChatResponse result, AiCoachChatRequest request) {
        if (result == null) {
            throw new BizException("AI 滑板老师返回结果为空");
        }
        if (!StringUtils.hasText(result.getReply())) {
            throw new BizException("AI 滑板老师回复为空");
        }
        result.setReply(result.getReply().trim());
        result.setSuggestions(normalizeSuggestions(result.getSuggestions(), request));
    }

    private void fillRelatedContent(AiCoachChatResponse result, AiCoachChatRequest request) {
        String question = request == null ? "" : request.getMessage();
        String reply = result == null ? "" : result.getReply();
        result.setRelatedPosts(aiCoachContentRecommendService.recommendPosts(question, reply));
        result.setRelatedVideos(aiCoachContentRecommendService.recommendVideos(question, reply));
        result.setRelatedActivities(aiCoachContentRecommendService.recommendActivities(question, reply));
    }

    public AiCoachContentRecommendResponse getRelatedContent(String question, String reply) {
        return new AiCoachContentRecommendResponse(
                aiCoachContentRecommendService.recommendPosts(question, reply),
                aiCoachContentRecommendService.recommendVideos(question, reply),
                aiCoachContentRecommendService.recommendActivities(question, reply)
        );
    }

    private AiCoachSession resolveSession(CurrentUser currentUser, AiCoachChatRequest request) {
        Long userId = requireUserId(currentUser);
        if (StringUtils.hasText(request.getSessionId())) {
            return getOwnedSession(currentUser, request.getSessionId().trim());
        }

        AiCoachSession session = new AiCoachSession();
        session.setSessionId("coach-" + UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setTitle(buildSessionTitle(request.getMessage()));
        return aiCoachSessionRepository.save(session);
    }

    private AiCoachSession getOwnedSession(CurrentUser currentUser, String sessionId) {
        Long userId = requireUserId(currentUser);
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException("会话不存在或无权访问");
        }
        return aiCoachSessionRepository.findBySessionIdAndUserId(sessionId.trim(), userId)
                .orElseThrow(() -> new BizException("会话不存在或无权访问"));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private String buildSessionTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return DEFAULT_SESSION_TITLE;
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SESSION_TITLE;
        }
        return normalized.length() <= SESSION_TITLE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, SESSION_TITLE_MAX_LENGTH);
    }

    private void saveMessages(String sessionId, String userContent, String assistantContent) {
        AiCoachMessage userMessage = new AiCoachMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("USER");
        userMessage.setContent(userContent);
        aiCoachMessageRepository.save(userMessage);

        AiCoachMessage assistantMessage = new AiCoachMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(assistantContent);
        aiCoachMessageRepository.save(assistantMessage);
    }

    private List<String> normalizeSuggestions(List<String> suggestions, AiCoachChatRequest request) {
        Set<String> normalized = new LinkedHashSet<>();
        if (suggestions != null) {
            for (String suggestion : suggestions) {
                if (StringUtils.hasText(suggestion)) {
                    normalized.add(suggestion.trim());
                }
                if (normalized.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        if (normalized.isEmpty()) {
           normalized.add("练习前先活动脚踝、膝盖和髋部，热身 5 到 10 分钟。");
           normalized.add("把动作拆成更小的步骤，先慢速做稳，再逐步连起来。");
           normalized.add("能讲讲练习前应该怎么热身吗");
           normalized.add("动作拆解后怎么一步步练更有效");
            if (containsRiskKeyword(request.getMessage())) {
                normalized.add("如果疼痛、肿胀或头晕持续，请先停止训练并及时就医。");
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean containsRiskKeyword(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("hurt")
                || lower.contains("injury")
                || lower.contains("pain")
                || lower.contains("sprain")
                || lower.contains("fall")
                || lower.contains("bleed")
                || lower.contains("摔")
                || lower.contains("疼")
                || lower.contains("伤")
                || lower.contains("流血");
    }
}
