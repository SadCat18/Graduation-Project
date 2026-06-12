package com.skatehub.pojo.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportHandleRequest {

    @NotBlank(message = "处理状态不能为空")
    @Pattern(regexp = "[12]", message = "处理状态不合法")
    private String status;

    @Size(max = 1000, message = "处理备注长度不能超过 1000")
    private String handleNote;
}
