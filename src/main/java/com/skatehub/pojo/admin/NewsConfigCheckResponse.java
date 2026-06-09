package com.skatehub.pojo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsConfigCheckResponse {

    private boolean enabled;
    private String provider;
    private String model;
    private boolean baseUrlConfigured;
    private boolean apiKeyConfigured;
    private boolean dedicatedNewsConfig;
    private List<String> missingItems;
}
