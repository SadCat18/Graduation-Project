package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiCoachReferenceResponse {

    private String sourceType;

    private Long sourceId;

    private String title;

    private String routePath;
}
