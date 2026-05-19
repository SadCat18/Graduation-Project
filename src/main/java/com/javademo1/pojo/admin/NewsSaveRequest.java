package com.javademo1.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewsSaveRequest {

    @NotBlank(message = "资讯标题不能为空")
    private String title;

    @NotBlank(message = "资讯内容不能为空")
    private String content;

    private String cover;
    private String category;
}

