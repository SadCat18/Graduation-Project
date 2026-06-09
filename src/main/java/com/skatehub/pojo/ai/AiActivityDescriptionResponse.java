package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiActivityDescriptionResponse {

    private String title;

    private String description;

    private List<String> highlights;

    private List<String> tips;

    private String suitableFor;

    private List<String> riskTips;
}
