package com.javademo1.pojo.video;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoCreateRequest {

    @NotBlank(message = "视频标题不能为空")
    private String title;

    private String cover;

    @NotBlank(message = "视频地址不能为空")
    private String url;

    private String intro;
}

