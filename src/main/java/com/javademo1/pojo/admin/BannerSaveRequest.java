package com.javademo1.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class BannerSaveRequest {

    @NotBlank(message = "轮播标题不能为空")
    private String title;

    @NotBlank(message = "轮播图片不能为空")
    private String imageUrl;

    private String linkUrl;
    private Integer sortNum;
    @Min(value = 2, message = "轮播间隔不能小于2秒")
    @Max(value = 60, message = "轮播间隔不能大于60秒")
    private Integer intervalSeconds;
    private String status;
}
