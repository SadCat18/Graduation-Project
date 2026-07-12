package com.skatehub.service.ai;

import com.skatehub.dao.AiCoachMessageRepository;
import com.skatehub.dao.AiCoachSessionRepository;
import com.skatehub.pojo.AiCoachMessage;
import com.skatehub.pojo.AiCoachSession;
import com.skatehub.pojo.ai.AiCoachChatRequest;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCoachAssistantRagTest {

    @Mock private AiGatewayService aiGatewayService;
    @Mock private AiCoachContentRecommendService aiCoachContentRecommendService;
    @Mock private AiCoachSessionRepository aiCoachSessionRepository;
    @Mock private AiCoachMessageRepository aiCoachMessageRepository;
    @Mock private AiCoachRagService aiCoachRagService;

    private AiCoachAssistantService service;

    @BeforeEach
    void setUp() {
        service = new AiCoachAssistantService(
                aiGatewayService,
                aiCoachContentRecommendService,
                aiCoachSessionRepository,
                aiCoachMessageRepository,
                aiCoachRagService
        );
        when(aiCoachSessionRepository.save(any(AiCoachSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiCoachMessageRepository.findTop12BySessionIdOrderByCreateTimeDescMsgIdDesc(any())).thenReturn(List.of());
    }

    @Test
    void injectsRagContextIntoCoachChatRequestAndReturnsReferences() {
        AiCoachChatRequest request = new AiCoachChatRequest();
        request.setMessage("Ollie 总是跳不起来怎么办");
        AiCoachRagChunk chunk = new AiCoachRagChunk(
                "TRICK",
                7L,
                "Ollie 基础练习",
                "后脚点板，前脚刷板，保持重心在板上方。",
                "/skate",
                42
        );
        when(aiCoachRagService.retrieve(request.getMessage())).thenReturn(new AiCoachRagContext(
                List.of(chunk),
                """
                Station reference material:
                [1] Type: TRICK
                Title: Ollie 基础练习
                Content: 后脚点板，前脚刷板，保持重心在板上方。
                Route: /skate
                """
        ));
        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        when(aiGatewayService.chat(captor.capture())).thenReturn(AiResponse.builder()
                .content("{\"reply\":\"先练点板和刷板。\",\"suggestions\":[\"怎么练刷板？\"]}")
                .build());

        var response = service.chat(new CurrentUser(1L, "USER", "tester"), request);

        assertThat(captor.getValue().getMessages())
                .anySatisfy(message -> assertThat(message.getContent())
                        .contains("Station reference material")
                        .contains("Ollie 基础练习")
                        .contains("/skate"));
        assertThat(captor.getValue().getSystemPrompt()).contains("references");
        assertThat(response.getReferences()).hasSize(1);
        assertThat(response.getReferences().get(0).getSourceType()).isEqualTo("TRICK");
        assertThat(response.getReferences().get(0).getRoutePath()).isEqualTo("/skate");
    }

    @Test
    void extractsCoachJsonWhenProviderWrapsItWithExtraText() {
        AiCoachChatRequest request = new AiCoachChatRequest();
        request.setMessage("Can I practice kickflip?");
        when(aiGatewayService.chat(any(AiRequest.class))).thenReturn(AiResponse.builder()
                .content("""
                        Here is the answer:
                        {
                          "reply": "Practice ollie and shuvit first.",
                          "suggestions": ["How do I know ollie is ready?", "How should I flick?"],
                          "references": [{"sourceType":"TRICK","sourceId":3,"title":"Kickflip","routePath":"/skate"}]
                        }</think_never_used_51bce0c785ca2f68081bfa7d91973934>{
                          "reply": "Practice ollie and shuvit first.",
                          "suggestions": ["How do I know ollie is ready?"],
                          "references": []
                        }
                        """)
                .build());

        var response = service.chat(new CurrentUser(1L, "USER", "tester"), request);

        assertThat(response.getReply()).isEqualTo("Practice ollie and shuvit first.");
        assertThat(response.getReply()).doesNotContain("\"reply\"").doesNotContain("think_never_used");
        assertThat(response.getSuggestions()).containsExactly("How do I know ollie is ready?", "How should I flick?");

        ArgumentCaptor<AiCoachMessage> savedMessages = ArgumentCaptor.forClass(AiCoachMessage.class);
        verify(aiCoachMessageRepository, atLeastOnce()).save(savedMessages.capture());
        assertThat(savedMessages.getAllValues())
                .filteredOn(message -> "ASSISTANT".equals(message.getRole()))
                .singleElement()
                .extracting(AiCoachMessage::getContent)
                .isEqualTo("Practice ollie and shuvit first.");
    }
}
