package com.skatehub.pojo.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportHandleRequest {

    @NotBlank(message = "处理状态不能为空")
    private String status;

    private String handleNote;
}
