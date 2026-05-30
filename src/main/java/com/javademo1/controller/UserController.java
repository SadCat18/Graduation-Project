package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.pojo.user.PasswordUpdateRequest;
import com.javademo1.pojo.user.UserProfileUpdateRequest;
import com.javademo1.pojo.Message;
import com.javademo1.pojo.User;
import com.javademo1.pojo.ReportRecord;
import com.javademo1.pojo.report.ReportCreateRequest;
import com.javademo1.pojo.place.PlaceReviewCreateRequest;
import com.javademo1.pojo.PlaceReview;
import com.javademo1.service.PlaceReviewService;
import com.javademo1.service.RecommendationService;
import com.javademo1.service.ReportService;
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
    private final ReportService reportService;
    private final PlaceReviewService placeReviewService;
    private final RecommendationService recommendationService;

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

    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications(@RequestParam(required = false) String readStatus,
                                                                 @RequestParam(required = false) String msgType) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.myNotifications(currentUser, readStatus, msgType));
    }

    @PutMapping("/messages/{id}/read")
    public ApiResponse<Void> readMessage(@PathVariable("id") Long messageId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        userService.readMessage(currentUser, messageId);
        return ApiResponse.success();
    }

    @PutMapping("/messages/read-all")
    public ApiResponse<Void> readAllMessages() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        userService.readAllMessages(currentUser);
        return ApiResponse.success();
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.dashboard(currentUser));
    }

    @GetMapping("/my-content")
    public ApiResponse<Map<String, Object>> myContent() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.myContent(currentUser));
    }

    @PostMapping("/reports")
    public ApiResponse<ReportRecord> report(@RequestBody @Valid ReportCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(reportService.create(currentUser, request));
    }

    @PostMapping("/place-reviews")
    public ApiResponse<PlaceReview> createPlaceReview(@RequestBody @Valid PlaceReviewCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(placeReviewService.create(currentUser, request));
    }

    @GetMapping("/recommendations")
    public ApiResponse<Map<String, Object>> recommendations() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(recommendationService.recommendForUser(currentUser.id()));
    }
}

