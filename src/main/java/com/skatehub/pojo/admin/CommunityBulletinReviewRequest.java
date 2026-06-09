package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityBulletinReviewRequest {

    @NotBlank(message = "审核状态不能为空")
    private String status;

    private String rejectReason;
}
