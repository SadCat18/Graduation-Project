package com.skatehub.pojo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAiStepResult<T> {

    private String scene;
    private String provider;
    private String model;
    private boolean success;
    private String errorMessage;
    private T data;
}
