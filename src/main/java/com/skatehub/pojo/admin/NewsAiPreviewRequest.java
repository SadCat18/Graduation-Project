package com.skatehub.pojo.admin;

import lombok.Data;

@Data
public class NewsAiPreviewRequest {

    private String originTitle;
    private String originContent;
    private String language;
    private String sourceName;
}
