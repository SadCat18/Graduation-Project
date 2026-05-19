package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.pojo.video.VideoCreateRequest;
import com.javademo1.pojo.Video;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.SecurityUtils;
import com.javademo1.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ApiResponse<Video> create(@RequestBody @Valid VideoCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(videoService.create(currentUser, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long videoId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        videoService.delete(currentUser, videoId, false);
        return ApiResponse.success();
    }
}

