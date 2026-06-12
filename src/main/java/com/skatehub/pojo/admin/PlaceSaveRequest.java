package com.skatehub.pojo.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceSaveRequest {

    @NotBlank(message = "场地名称不能为空")
    @Size(max = 100, message = "场地名称长度不能超过 100")
    private String name;

    @Size(max = 200, message = "场地地址长度不能超过 200")
    private String address;
    @Size(max = 2000, message = "场地介绍长度不能超过 2000")
    private String intro;
    @DecimalMin(value = "0.0", message = "评分不能小于 0")
    @DecimalMax(value = "5.0", message = "评分不能大于 5")
    private BigDecimal score;
}
