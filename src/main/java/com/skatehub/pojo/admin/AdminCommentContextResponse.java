package com.skatehub.pojo.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminCommentContextResponse {

    private Long postId;
    private String postTitle;
    private String postContentPreview;
    private LocalDateTime postCreateTime;
    private AdminCommentResponse currentComment;
    private AdminCommentResponse parentComment;
    private List<AdminCommentResponse> nearbyComments;
}
