package com.skatehub.pojo.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VideoCreateRequest {

    @NotBlank(message = "视频标题不能为空")
    @Size(max = 100, message = "视频标题长度不能超过 100")
    private String title;

    @Size(max = 500, message = "封面地址长度不能超过 500")
    private String cover;

    @NotBlank(message = "视频地址不能为空")
    @Size(max = 500, message = "视频地址长度不能超过 500")
    private String url;

    @Size(max = 1000, message = "视频简介长度不能超过 1000")
    private String intro;
}
