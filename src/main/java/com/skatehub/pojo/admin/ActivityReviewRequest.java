package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivityReviewRequest {

    @NotBlank(message = "审核状态不能为空")
    private String status;
}
