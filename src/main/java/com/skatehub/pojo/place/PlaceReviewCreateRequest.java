package com.skatehub.pojo.place;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceReviewCreateRequest {
    @NotNull(message = "请先选择要评价的场地")
    private Long placeId;

    @NotNull(message = "请填写评分")
    @Min(value = 1, message = "评分范围应为 1 到 5 分")
    @Max(value = 5, message = "评分范围应为 1 到 5 分")
    private Integer score;

    private String content;

    private String images;
}
