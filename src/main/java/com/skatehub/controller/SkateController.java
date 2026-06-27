package com.skatehub.controller;

import com.skatehub.pojo.skate.TrickCheckinRequest;
import com.skatehub.pojo.skate.TrainingTaskCompleteRequest;
import com.skatehub.service.EquipmentService;
import com.skatehub.service.SkateEventService;
import com.skatehub.service.TrainingPlanService;
import com.skatehub.service.TrickService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skate")
@RequiredArgsConstructor
@Validated
public class SkateController {

    private final TrickService trickService;
    private final TrainingPlanService trainingPlanService;
    private final SkateEventService skateEventService;
    private final EquipmentService equipmentService;

    @GetMapping("/tricks")
    public ApiResponse<?> tricks(@RequestParam(required = false) @Size(max = 40) String category) {
        return ApiResponse.success(trickService.list(category));
    }

    @GetMapping("/tricks/{trickId}")
    public ApiResponse<?> trickDetail(@PathVariable @Positive(message = "trickId必须为正数") Long trickId) {
        return ApiResponse.success(trickService.detail(trickId));
    }

    @PostMapping("/tricks/{trickId}/checkin")
    public ApiResponse<Map<String, Object>> submitTrickCheckin(@PathVariable @Positive(message = "trickId必须为正数") Long trickId,
                                                               @RequestBody @Valid TrickCheckinRequest request) {
        return ApiResponse.success(trickService.submitCheckin(SecurityUtils.currentUser(), trickId, request));
    }

    @GetMapping("/tricks/{trickId}/checkins")
    public ApiResponse<?> trickCheckins(@PathVariable @Positive(message = "trickId必须为正数") Long trickId) {
        return ApiResponse.success(trickService.history(SecurityUtils.currentUser(), trickId));
    }

    @GetMapping("/training-plans")
    public ApiResponse<?> trainingPlans() {
        return ApiResponse.success(trainingPlanService.listPlans());
    }

    @GetMapping("/training-plans/{planId}")
    public ApiResponse<Map<String, Object>> trainingPlanDetail(@PathVariable @Positive(message = "planId必须为正数") Long planId) {
        return ApiResponse.success(trainingPlanService.detail(planId));
    }

    @PostMapping("/training-plans/{planId}/join")
    public ApiResponse<Map<String, Object>> joinTrainingPlan(@PathVariable @Positive(message = "planId必须为正数") Long planId) {
        return ApiResponse.success(trainingPlanService.join(SecurityUtils.currentUser(), planId));
    }

    @PostMapping("/training-plans/{userPlanId}/quit")
    public ApiResponse<Void> quitTrainingPlan(@PathVariable @Positive(message = "userPlanId必须为正数") Long userPlanId) {
        trainingPlanService.quit(SecurityUtils.currentUser(), userPlanId);
        return ApiResponse.success();
    }

    @PostMapping("/training-tasks/{taskId}/complete")
    public ApiResponse<Map<String, Object>> completeTrainingTask(@PathVariable @Positive(message = "taskId必须为正数") Long taskId,
                                                                 @RequestBody @Valid TrainingTaskCompleteRequest request) {
        return ApiResponse.success(trainingPlanService.completeTask(SecurityUtils.currentUser(), taskId, request));
    }

    @GetMapping("/events")
    public ApiResponse<PageResult<Map<String, Object>>> events(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                               @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                               @RequestParam(required = false) @Size(max = 50) String city,
                                                               @RequestParam(required = false) @Size(max = 30) String eventType,
                                                               @RequestParam(required = false) @Size(max = 30) String status) {
        CurrentUser user = optionalCurrentUser();
        return ApiResponse.success(skateEventService.list(page, size, user == null ? null : user.id(), city, eventType, status));
    }

    @GetMapping("/events/{eventId}")
    public ApiResponse<Map<String, Object>> eventDetail(@PathVariable @Positive(message = "eventId必须为正数") Long eventId) {
        CurrentUser user = optionalCurrentUser();
        return ApiResponse.success(skateEventService.detail(eventId, user == null ? null : user.id()));
    }

    @PostMapping("/events/{eventId}/signup")
    public ApiResponse<Map<String, Object>> signupEvent(@PathVariable @Positive(message = "eventId必须为正数") Long eventId) {
        return ApiResponse.success(skateEventService.signup(SecurityUtils.currentUser(), eventId));
    }

    @PostMapping("/events/{eventId}/cancel")
    public ApiResponse<Void> cancelEvent(@PathVariable @Positive(message = "eventId必须为正数") Long eventId) {
        skateEventService.cancel(SecurityUtils.currentUser(), eventId);
        return ApiResponse.success();
    }

    @PostMapping("/events/{eventId}/checkin")
    public ApiResponse<Map<String, Object>> checkinEvent(@PathVariable @Positive(message = "eventId必须为正数") Long eventId) {
        return ApiResponse.success(skateEventService.checkin(SecurityUtils.currentUser(), eventId));
    }

    @GetMapping("/equipments")
    public ApiResponse<PageResult<Map<String, Object>>> equipments(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                                   @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                                   @RequestParam(required = false) @Size(max = 30) String category,
                                                                   @RequestParam(required = false) @Size(max = 60) String brand,
                                                                   @RequestParam(required = false) @Size(max = 60) String priceRange,
                                                                   @RequestParam(required = false) @Size(max = 40) String suitableLevel) {
        CurrentUser user = optionalCurrentUser();
        return ApiResponse.success(equipmentService.list(page, size, user == null ? null : user.id(), category, brand, priceRange, suitableLevel));
    }

    @GetMapping("/equipments/{equipmentId}")
    public ApiResponse<Map<String, Object>> equipmentDetail(@PathVariable @Positive(message = "equipmentId必须为正数") Long equipmentId) {
        CurrentUser user = optionalCurrentUser();
        return ApiResponse.success(equipmentService.detail(equipmentId, user == null ? null : user.id()));
    }

    @PostMapping("/equipments/{equipmentId}/favorite")
    public ApiResponse<Map<String, Object>> favoriteEquipment(@PathVariable @Positive(message = "equipmentId必须为正数") Long equipmentId) {
        return ApiResponse.success(equipmentService.favorite(SecurityUtils.currentUser(), equipmentId));
    }

    @DeleteMapping("/equipments/{equipmentId}/favorite")
    public ApiResponse<Void> unfavoriteEquipment(@PathVariable @Positive(message = "equipmentId必须为正数") Long equipmentId) {
        equipmentService.unfavorite(SecurityUtils.currentUser(), equipmentId);
        return ApiResponse.success();
    }

    private CurrentUser optionalCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
            return currentUser;
        }
        return null;
    }
}
