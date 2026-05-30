package com.javademo1.pojo.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityCreateRequest {

    @NotBlank(message = "娲诲姩鏍囬涓嶈兘涓虹┖")
    private String title;

    private String content;

    @NotBlank(message = "娲诲姩璇存槑涓嶈兘涓虹┖")
    @Size(min = 10, message = "娲诲姩璇存槑涓嶈兘杩囩煭")
    private String activityDesc;

    @NotBlank(message = "娲诲姩绫诲瀷涓嶈兘涓虹┖")
    private String activityType;

    @NotBlank(message = "活动地点不能为空")
    private String place;

    private Long placeId;

    private String address;
    private String city;
    private String district;
    private BigDecimal longitude;
    private BigDecimal latitude;

    @NotNull(message = "娲诲姩鏃堕棿涓嶈兘涓虹┖")
    private LocalDateTime activityTime;

    @NotNull(message = "浜烘暟涓婇檺涓嶈兘涓虹┖")
    private Integer maxNum;
}

