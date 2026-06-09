package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiResponse {

    private String provider;

    private String model;

    /**
     * Unified output text for frontend consumption.
     */
    private String content;

    /**
     * Raw provider response JSON for troubleshooting or audit.
     */
    private String rawJson;

    private String finishReason;

    private AiUsage usage;

    /**
     * Assistant messages returned by the provider.
     */
    private List<AiMessage> messages;
}
