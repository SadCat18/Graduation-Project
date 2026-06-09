package com.skatehub.pojo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAiReprocessItemResponse {

    private Long newsId;
    private boolean success;
    private boolean skipped;
    private String message;
}
