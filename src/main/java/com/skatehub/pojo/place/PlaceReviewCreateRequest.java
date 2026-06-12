package com.skatehub.pojo.place;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlaceReviewCreateRequest {
    @NotNull(message = "请先选择要评价的场地")
    @Positive(message = "场地ID必须为正数")
    private Long placeId;

    @NotNull(message = "请填写评分")
    @Min(value = 1, message = "评分范围应为 1 到 5 分")
    @Max(value = 5, message = "评分范围应为 1 到 5 分")
    private Integer score;

    @Size(max = 1000, message = "评价内容长度不能超过 1000")
    private String content;

    @Size(max = 4000, message = "评价图片地址长度不能超过 4000")
    private String images;
}
