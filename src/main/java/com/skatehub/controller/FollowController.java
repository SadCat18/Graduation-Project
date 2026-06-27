package com.skatehub.controller;

import com.skatehub.service.FollowService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}/follow")
    public ApiResponse<Map<String, Object>> follow(@PathVariable @Positive(message = "用户ID必须为正数") Long userId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(followService.follow(currentUser, userId));
    }

    @DeleteMapping("/{userId}/follow")
    public ApiResponse<Map<String, Object>> unfollow(@PathVariable @Positive(message = "用户ID必须为正数") Long userId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(followService.unfollow(currentUser, userId));
    }

    @GetMapping("/{userId}/follow-status")
    public ApiResponse<Map<String, Object>> status(@PathVariable @Positive(message = "用户ID必须为正数") Long userId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(followService.status(currentUser, userId));
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<Map<String, Object>>> following(@PathVariable @Positive(message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(followService.following(userId));
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<Map<String, Object>>> followers(@PathVariable @Positive(message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(followService.followers(userId));
    }
}
