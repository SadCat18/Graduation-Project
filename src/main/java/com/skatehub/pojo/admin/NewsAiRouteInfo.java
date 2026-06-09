package com.skatehub.pojo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAiRouteInfo {

    private String scene;
    private String provider;
    private String model;
    private boolean baseUrlConfigured;
    private boolean apiKeyConfigured;
    private boolean newsScene;
    private boolean dedicatedNewsConfig;
    private boolean sceneOverrideConfigured;
    private boolean fallbackToDefault;
    private boolean success;
    private String errorMessage;
}
