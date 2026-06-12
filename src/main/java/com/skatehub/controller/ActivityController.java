package com.skatehub.controller;

import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.activity.ActivityCreateRequest;
import com.skatehub.pojo.activity.ActivitySignStatusUpdateRequest;
import com.skatehub.service.ActivityService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.PageResult;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                             @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                             @RequestParam(required = false) @Size(max = 50) String city,
                                                             @RequestParam(required = false) @Size(max = 50) String district,
                                                             @RequestParam(required = false) @Size(max = 50) String keyword,
                                                             @RequestParam(required = false) Boolean expired) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.list(page, size, currentUser.id(), city, district, keyword, expired));
    }

    @PostMapping
    public ApiResponse<Activity> create(@RequestBody @Valid ActivityCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.create(currentUser, request));
    }

    @PostMapping("/{id}/sign")
    public ApiResponse<Map<String, Object>> sign(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.sign(currentUser, activityId));
    }

    @PutMapping("/{id}/close-signup")
    public ApiResponse<Activity> closeSignup(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.closeSignup(currentUser, activityId));
    }

    @PutMapping("/{id}/sign/cancel")
    public ApiResponse<Void> cancelSign(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        activityService.cancelMySign(currentUser, activityId);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/checkin")
    public ApiResponse<Void> checkin(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        activityService.checkin(currentUser, activityId);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/signs")
    public ApiResponse<List<ActivitySign>> signs(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.signs(currentUser, activityId));
    }

    @PutMapping("/{id}/signs/{signId}/status")
    public ApiResponse<ActivitySign> updateSignStatus(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId,
                                                      @PathVariable @Positive(message = "signId必须为正数") Long signId,
                                                      @RequestBody @Valid ActivitySignStatusUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.updateSignStatus(currentUser, activityId, signId, request.getSignStatus()));
    }

    @GetMapping("/signed/me")
    public ApiResponse<List<Map<String, Object>>> mySignedActivities() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.mySignedActivities(currentUser));
    }

    @GetMapping("/published/me")
    public ApiResponse<List<Map<String, Object>>> myPublishedActivities() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(activityService.myPublishedActivities(currentUser));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        activityService.delete(currentUser, activityId, false);
        return ApiResponse.success();
    }
}
