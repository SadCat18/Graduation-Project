package com.skatehub.service;

import com.skatehub.pojo.ai.AiModerationSuggestRequest;
import com.skatehub.pojo.ai.AiModerationSuggestResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationRuleServiceTest {

    @Test
    void flagsContactAndExternalLinkBeforeAiReview() {
        ModerationRuleService service = new ModerationRuleService();
        AiModerationSuggestRequest request = new AiModerationSuggestRequest();
        request.setContentType("POST");
        request.setTitle("Cheap board group");
        request.setContent("Add me on QQ 123456 and click http://spam.example for private deal.");

        AiModerationSuggestResponse result = service.evaluate(request);

        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getRiskPoints()).contains("EXTERNAL_LINK", "CONTACT_INFO");
        assertThat(result.getSuggestion()).contains("manual review");
    }

    @Test
    void lowRiskContentStillReturnsNormalizedSummary() {
        ModerationRuleService service = new ModerationRuleService();
        AiModerationSuggestRequest request = new AiModerationSuggestRequest();
        request.setContentType("ACTIVITY");
        request.setTitle("Saturday beginner flatground");
        request.setContent("Meet at the park, bring helmet, practice pushing and braking.");

        AiModerationSuggestResponse result = service.evaluate(request);

        assertThat(result.getRiskLevel()).isEqualTo("LOW");
        assertThat(result.getRiskPoints()).isEmpty();
        assertThat(result.getNormalizedSummary()).contains("Saturday beginner flatground");
    }

    @Test
    void publishableCheckRejectsRepeatedTextAndTooManyLinks() {
        ModerationRuleService service = new ModerationRuleService();

        assertThatThrownBy(() -> service.assertPublishable("POST", "spam", "滑板滑板滑板滑板滑板滑板"))
                .hasMessageContaining("重复文本");
        assertThatThrownBy(() -> service.assertPublishable("POST", "links", "看 http://a.test http://b.test http://c.test http://d.test"))
                .hasMessageContaining("外部链接");
    }
}
