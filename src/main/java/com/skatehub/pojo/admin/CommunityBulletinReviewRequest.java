package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityBulletinReviewRequest {

    @NotBlank(message = "审核状态不能为空")
    @Pattern(regexp = "[12]", message = "审核状态不合法")
    private String status;

    @Size(max = 500, message = "驳回原因长度不能超过 500")
    private String rejectReason;
}
