package com.skatehub.pojo.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ActivitySignStatusUpdateRequest {

    @NotBlank(message = "报名状态不能为空")
    @Pattern(regexp = "[123]", message = "报名状态不合法")
    private String signStatus;
}
