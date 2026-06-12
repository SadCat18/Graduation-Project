package com.skatehub.pojo.activity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityCreateRequest {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题长度不能超过 100")
    private String title;

    @Size(max = 10000, message = "活动内容长度不能超过 10000")
    private String content;

    @NotBlank(message = "活动说明不能为空")
    @Size(min = 10, max = 2000, message = "活动说明长度需在 10 到 2000 之间")
    private String activityDesc;

    @NotBlank(message = "活动类型不能为空")
    @Size(max = 30, message = "活动类型长度不能超过 30")
    private String activityType;

    @NotBlank(message = "活动地点不能为空")
    @Size(max = 100, message = "活动地点长度不能超过 100")
    private String place;

    @Positive(message = "场地ID必须为正数")
    private Long placeId;

    @Size(max = 200, message = "地址长度不能超过 200")
    private String address;
    @Size(max = 50, message = "城市长度不能超过 50")
    private String city;
    @Size(max = 50, message = "区县长度不能超过 50")
    private String district;
    private BigDecimal longitude;
    private BigDecimal latitude;

    @NotNull(message = "活动时间不能为空")
    private LocalDateTime activityTime;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 2, message = "人数上限不能小于 2")
    @Max(value = 50, message = "人数上限不能超过 50")
    private Integer maxNum;
}
