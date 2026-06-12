package com.skatehub.pojo.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BannerSaveRequest {

    @NotBlank(message = "轮播标题不能为空")
    @Size(max = 100, message = "轮播标题长度不能超过 100")
    private String title;

    @NotBlank(message = "轮播图片不能为空")
    @Size(max = 500, message = "轮播图片地址长度不能超过 500")
    private String imageUrl;

    @Size(max = 500, message = "跳转地址长度不能超过 500")
    private String linkUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    @Max(value = 9999, message = "排序值不能超过 9999")
    private Integer sortNum;

    @Min(value = 2, message = "轮播间隔不能小于2秒")
    @Max(value = 60, message = "轮播间隔不能大于60秒")
    private Integer intervalSeconds;

    @Pattern(regexp = "[01]", message = "轮播状态不合法")
    private String status;
}
