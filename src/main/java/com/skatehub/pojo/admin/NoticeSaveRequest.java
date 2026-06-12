package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoticeSaveRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题长度不能超过 100")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容长度不能超过 5000")
    private String content;

    @Pattern(regexp = "[01]", message = "公告状态不合法")
    private String status;
}
