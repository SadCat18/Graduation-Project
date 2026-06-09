package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiModerationSuggestResponse {

    private String riskLevel;

    private List<String> riskPoints;

    private String suggestion;

    private String normalizedSummary;
}
