package com.skatehub.pojo.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityBulletinCreateRequest {

    @NotBlank(message = "快讯标题不能为空")
    @Size(max = 100, message = "快讯标题长度不能超过 100")
    private String title;

    @NotBlank(message = "快讯内容不能为空")
    @Size(max = 5000, message = "快讯内容长度不能超过 5000")
    private String content;

    @NotBlank(message = "快讯类型不能为空")
    @Size(max = 30, message = "快讯类型长度不能超过 30")
    private String bulletinType;

    @Size(max = 4000, message = "快讯图片地址长度不能超过 4000")
    private String imageUrls;
}
