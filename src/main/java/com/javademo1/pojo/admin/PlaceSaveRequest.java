package com.javademo1.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceSaveRequest {

    @NotBlank(message = "场地名称不能为空")
    private String name;

    private String address;
    private String intro;
    private BigDecimal score;
}

