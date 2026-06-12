package com.skatehub.pojo.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordUpdateRequest {

    @NotBlank(message = "旧密码不能为空")
    @Size(max = 72, message = "旧密码长度不能超过 72")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需在 8 到 20 位之间")
    private String newPassword;
}
