package com.skatehub.pojo.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostBatchTopRequest {

    @Size(max = 50, message = "批量帖子数量不能超过 50")
    private List<@Positive(message = "帖子ID必须为正数") Long> postIds;

    @Pattern(regexp = "[01]", message = "置顶值不合法")
    private String top;
}
