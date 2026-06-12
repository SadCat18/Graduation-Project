package com.skatehub.pojo.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;

    @Size(max = 20000, message = "正文长度不能超过 20000")
    private String content;

    @Size(max = 4000, message = "图片地址长度不能超过 4000")
    private String images;

    @Size(max = 30, message = "分类长度不能超过 30")
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9_\\-\\s]*$", message = "分类格式不合法")
    private String category;
}
