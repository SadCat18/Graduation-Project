package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.pojo.user.PasswordUpdateRequest;
import com.javademo1.pojo.user.UserProfileUpdateRequest;
import com.javademo1.pojo.Message;
import com.javademo1.pojo.User;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.SecurityUtils;
import com.javademo1.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<User> profile() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.profile(currentUser));
    }

    @PutMapping("/profile")
    public ApiResponse<User> updateProfile(@RequestBody UserProfileUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.updateProfile(currentUser, request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@RequestBody @Valid PasswordUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        userService.updatePassword(currentUser, request);
        return ApiResponse.success();
    }

    @GetMapping("/messages")
    public ApiResponse<List<Message>> messages() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.myMessages(currentUser));
    }

    @PutMapping("/messages/{id}/read")
    public ApiResponse<Void> readMessage(@PathVariable("id") Long messageId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        userService.readMessage(currentUser, messageId);
        return ApiResponse.success();
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.dashboard(currentUser));
    }
}

