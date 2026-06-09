package com.skatehub.pojo.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityCreateRequest {

    @NotBlank(message = "活动标题不能为空")
    private String title;

    private String content;

    @NotBlank(message = "活动说明不能为空")
    @Size(min = 10, message = "活动说明不能过短")
    private String activityDesc;

    @NotBlank(message = "活动类型不能为空")
    private String activityType;

    @NotBlank(message = "活动地点不能为空")
    private String place;

    private Long placeId;

    private String address;
    private String city;
    private String district;
    private BigDecimal longitude;
    private BigDecimal latitude;

    @NotNull(message = "活动时间不能为空")
    private LocalDateTime activityTime;

    @NotNull(message = "人数上限不能为空")
    private Integer maxNum;
}
