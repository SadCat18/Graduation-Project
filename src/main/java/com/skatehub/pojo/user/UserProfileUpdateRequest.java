package com.skatehub.pojo.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 255, message = "头像地址长度不能超过 255")
    private String avatar;

    @Pattern(regexp = "^$|[012]", message = "性别值不合法")
    private String gender;

    @Size(max = 30, message = "滑板风格长度不能超过 30")
    private String skateStyle;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确，请输入 11 位手机号")
    private String phone;

    @Size(max = 200, message = "个人简介长度不能超过 200")
    private String bio;
}
