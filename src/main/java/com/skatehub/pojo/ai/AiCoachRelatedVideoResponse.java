package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiCoachRelatedVideoResponse {

    private Long videoId;

    private String title;

    private String intro;

    private String cover;

    private String url;

    private LocalDateTime createTime;
}
