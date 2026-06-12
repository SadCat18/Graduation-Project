package com.skatehub.pojo.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportCreateRequest {

    @NotBlank(message = "举报目标类型不能为空")
    @Pattern(regexp = "POST|COMMENT|BULLETIN", message = "举报目标类型不合法")
    private String targetType;

    @NotNull(message = "举报目标ID不能为空")
    @Positive(message = "举报目标ID必须为正数")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 30, message = "举报原因长度不能超过 30")
    private String reason;

    @Size(max = 1000, message = "举报详情长度不能超过 1000")
    private String detail;
}
