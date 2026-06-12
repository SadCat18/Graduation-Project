package com.skatehub.pojo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @Size(max = 20, message = "登录类型长度不能超过 20")
    private String loginType;

    @NotBlank(message = "账号不能为空")
    @Size(max = 50, message = "账号长度不能超过 50")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码长度不能超过 72")
    private String password;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 6, message = "验证码长度不合法")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "验证码格式不合法")
    private String captchaCode;

    @NotBlank(message = "验证码ID不能为空")
    @Size(max = 64, message = "验证码ID长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "验证码ID格式不合法")
    private String captchaId;
}
