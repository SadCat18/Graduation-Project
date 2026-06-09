package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoticeSaveRequest {

    @NotBlank(message = "公告标题不能为空")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private String status;
}
