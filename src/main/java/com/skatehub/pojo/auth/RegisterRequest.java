package com.skatehub.pojo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度需在 2 到 20 位之间")
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9_\\-]+$", message = "用户名仅支持中文、字母、数字、下划线和短横线")
    private String username;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确，请输入11位手机号")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需在 8 到 20 位之间")
    private String password;
}
