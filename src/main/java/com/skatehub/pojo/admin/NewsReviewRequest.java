package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class NewsReviewRequest {

    @NotBlank(message = "资讯审核状态不能为空")
    @Pattern(regexp = "[012]", message = "资讯审核状态不合法")
    private String status;
}
