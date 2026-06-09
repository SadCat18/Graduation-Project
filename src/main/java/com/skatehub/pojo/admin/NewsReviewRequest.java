package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewsReviewRequest {

    @NotBlank(message = "资讯审核状态不能为空")
    private String status;
}
