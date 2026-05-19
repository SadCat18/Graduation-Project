package com.javademo1.pojo.user;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String avatar;
    private String gender;
    private String skateStyle;
    private String phone;
    private String bio;
}

