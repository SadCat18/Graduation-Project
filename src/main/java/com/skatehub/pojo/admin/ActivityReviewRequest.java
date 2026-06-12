package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ActivityReviewRequest {

    @NotBlank(message = "审核状态不能为空")
    @Pattern(regexp = "[12]", message = "审核状态不合法")
    private String status;
}
