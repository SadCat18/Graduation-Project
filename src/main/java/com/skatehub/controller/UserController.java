package com.skatehub.controller;

import com.skatehub.util.ApiResponse;
import com.skatehub.pojo.user.PasswordUpdateRequest;
import com.skatehub.pojo.user.UserProfileUpdateRequest;
import com.skatehub.pojo.Message;
import com.skatehub.pojo.User;
import com.skatehub.pojo.ReportRecord;
import com.skatehub.pojo.report.ReportCreateRequest;
import com.skatehub.pojo.place.PlaceReviewCreateRequest;
import com.skatehub.pojo.PlaceReview;
import com.skatehub.service.PlaceReviewService;
import com.skatehub.service.RecommendationService;
import com.skatehub.service.ReportService;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import com.skatehub.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
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
    public ApiResponse<User> updateProfile(@RequestBody @Valid UserProfileUpdateRequest request) {
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
    public ApiResponse<List<Map<String, Object>>> notifications(@RequestParam(required = false) @Pattern(regexp = "(?i)all|[01]", message = "消息状态不合法") String readStatus,
                                                                 @RequestParam(required = false) @Pattern(regexp = "(?i)all|comment|like|activity|system", message = "消息类型不合法") String msgType) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(userService.myNotifications(currentUser, readStatus, msgType));
    }

    @PutMapping("/messages/{id}/read")
    public ApiResponse<Void> readMessage(@PathVariable("id") @Positive(message = "id必须为正数") Long messageId) {
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
