package com.skatehub.controller;

import com.skatehub.util.ApiResponse;
import com.skatehub.pojo.video.VideoCreateRequest;
import com.skatehub.pojo.Video;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import com.skatehub.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Validated
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ApiResponse<Video> create(@RequestBody @Valid VideoCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(videoService.create(currentUser, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") @Positive(message = "id必须为正数") Long videoId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        videoService.delete(currentUser, videoId, false);
        return ApiResponse.success();
    }
}
