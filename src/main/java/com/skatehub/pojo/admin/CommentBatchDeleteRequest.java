package com.skatehub.pojo.admin;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommentBatchDeleteRequest {

    @Size(max = 50, message = "批量评论数量不能超过 50")
    private List<@Positive(message = "评论ID必须为正数") Long> commentIds;
}
