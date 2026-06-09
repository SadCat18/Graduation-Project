package com.skatehub.pojo.community;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityBulletinCreateRequest {

    @NotBlank(message = "快讯标题不能为空")
    private String title;

    @NotBlank(message = "快讯内容不能为空")
    private String content;

    @NotBlank(message = "快讯类型不能为空")
    private String bulletinType;

    private String imageUrls;
}
