package com.skatehub.pojo.post;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostCreateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;
    private String images;
    private String category;
}
