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
public class AiCoachChatResponse {

    private String sessionId;

    private String reply;

    private List<String> suggestions;

    private List<AiCoachRelatedPostResponse> relatedPosts;

    private List<AiCoachRelatedVideoResponse> relatedVideos;

    private List<AiCoachRelatedActivityResponse> relatedActivities;
}
