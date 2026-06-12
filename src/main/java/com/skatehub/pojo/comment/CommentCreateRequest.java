package com.skatehub.pojo.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容长度不能超过 2000")
    private String content;

    @PositiveOrZero(message = "父评论ID不能为负数")
    private Long parentId = 0L;
}
