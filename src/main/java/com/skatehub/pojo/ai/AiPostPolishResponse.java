package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiPostPolishResponse {

    private String title;

    private String content;

    private String category;

    private String summary;

    private List<String> riskTips;
}
