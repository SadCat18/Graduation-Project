package com.skatehub.controller;

import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.pojo.ai.AiCoachChatRequest;
import com.skatehub.pojo.ai.AiCoachChatResponse;
import com.skatehub.pojo.ai.AiCoachContentRecommendResponse;
import com.skatehub.pojo.ai.AiCoachSessionItemResponse;
import com.skatehub.pojo.ai.AiCoachSessionMessagesResponse;
import com.skatehub.pojo.ai.AiPostPolishRequest;
import com.skatehub.pojo.ai.AiPostPolishResponse;
import com.skatehub.pojo.ai.AiActivityDescriptionRequest;
import com.skatehub.pojo.ai.AiActivityDescriptionResponse;
import com.skatehub.pojo.ai.AiModerationSuggestRequest;
import com.skatehub.pojo.ai.AiModerationSuggestResponse;
import com.skatehub.service.ai.AiActivityAssistantService;
import com.skatehub.service.ai.AiCoachAssistantService;
import com.skatehub.service.ai.AiModerationAssistantService;
import com.skatehub.service.ai.AiPostAssistantService;
import com.skatehub.service.ai.AiGatewayService;
import com.skatehub.service.ai.AiSceneService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.BizException;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiActivityAssistantService aiActivityAssistantService;
    private final AiCoachAssistantService aiCoachAssistantService;
    private final AiModerationAssistantService aiModerationAssistantService;
    private final AiPostAssistantService aiPostAssistantService;
    private final AiGatewayService aiGatewayService;
    private final AiSceneService aiSceneService;

    @PostMapping("/activity-description")
    public ApiResponse<AiActivityDescriptionResponse> activityDescription(
            @Valid @RequestBody AiActivityDescriptionRequest request) {
        SecurityUtils.currentUser();
        return ApiResponse.success(aiActivityAssistantService.generateDescription(request));
    }

    @PostMapping("/moderation-suggest")
    public ApiResponse<AiModerationSuggestResponse> moderationSuggest(
            @Valid @RequestBody AiModerationSuggestRequest request) {
        ensureAdmin();
        return ApiResponse.success(aiModerationAssistantService.suggest(request));
    }

    @PostMapping("/post-polish")
    public ApiResponse<AiPostPolishResponse> postPolish(@Valid @RequestBody AiPostPolishRequest request) {
        SecurityUtils.currentUser();
        return ApiResponse.success(aiPostAssistantService.polish(request));
    }

    @PostMapping("/coach/chat")
    public ApiResponse<AiCoachChatResponse> coachChat(@Valid @RequestBody AiCoachChatRequest request) {
        return ApiResponse.success(aiCoachAssistantService.chat(SecurityUtils.currentUser(), request));
    }

    @GetMapping("/coach/sessions")
    public ApiResponse<List<AiCoachSessionItemResponse>> coachSessions() {
        return ApiResponse.success(aiCoachAssistantService.listSessions(SecurityUtils.currentUser()));
    }

    @DeleteMapping("/coach/sessions/{id}")
    public ApiResponse<Void> coachDeleteSession(@PathVariable("id") String sessionId) {
        aiCoachAssistantService.deleteSession(SecurityUtils.currentUser(), sessionId);
        return ApiResponse.success();
    }


    @GetMapping("/coach/sessions/{id}/messages")
    public ApiResponse<AiCoachSessionMessagesResponse> coachSessionMessages(@PathVariable("id") String sessionId) {
        return ApiResponse.success(aiCoachAssistantService.getSessionMessages(SecurityUtils.currentUser(), sessionId));
    }

    @GetMapping("/coach/related-content")
    public ApiResponse<AiCoachContentRecommendResponse> coachRelatedContent(
            @RequestParam("question") String question,
            @RequestParam("reply") String reply) {
       return ApiResponse.success(aiCoachAssistantService.getRelatedContent(question, reply));
   }

    @PostMapping("/chat")
    public ApiResponse<AiResponse> chat(@Valid @RequestBody AiRequest request) {
        return ApiResponse.success(aiGatewayService.chat(request));
    }

    @PostMapping("/execute")
    public ApiResponse<AiResponse> execute(@Valid @RequestBody AiRequest request) {
        return ApiResponse.success(aiSceneService.execute(request));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        var defaultConfig = aiGatewayService.config().resolveSceneConfig(null);
        var newsConfig = aiGatewayService.config().resolveSceneConfig("news_summary");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", defaultConfig.getProvider());
        result.put("model", defaultConfig.getModel());
        result.put("baseUrl", defaultConfig.getBaseUrl());
        result.put("configured", defaultConfig.hasApiKey());
        result.put("newsProvider", newsConfig.getProvider());
        result.put("newsModel", newsConfig.getModel());
        return ApiResponse.success(result);
    }

    private void ensureAdmin() {
        SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin()) {
            throw new BizException("仅管理员可调用 AI 审核辅助接口");
        }
    }
}
