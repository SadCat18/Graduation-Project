package com.skatehub.pojo.activity;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivitySignStatusUpdateRequest {

    @NotBlank(message = "报名状态不能为空")
    private String signStatus;
}
