package com.skatehub.controller;

import com.skatehub.service.EquipmentService;
import com.skatehub.service.SkateEventService;
import com.skatehub.service.TrainingPlanService;
import com.skatehub.service.TrickService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserSkateController {

    private final TrickService trickService;
    private final TrainingPlanService trainingPlanService;
    private final SkateEventService skateEventService;
    private final EquipmentService equipmentService;

    @GetMapping("/trick-checkins")
    public ApiResponse<?> trickCheckins() {
        return ApiResponse.success(trickService.myCheckins(SecurityUtils.currentUser()));
    }

    @GetMapping("/training-plans/current")
    public ApiResponse<?> currentTrainingPlan() {
        return ApiResponse.success(trainingPlanService.current(SecurityUtils.currentUser()));
    }

    @PostMapping("/training-tasks/{taskId}/complete")
    public ApiResponse<?> completeTrainingTaskAlias(@PathVariable Long taskId,
                                                   @RequestBody @jakarta.validation.Valid com.skatehub.pojo.skate.TrainingTaskCompleteRequest request) {
        return ApiResponse.success(trainingPlanService.completeTask(SecurityUtils.currentUser(), taskId, request));
    }

    @GetMapping("/training-records")
    public ApiResponse<?> trainingRecords() {
        return ApiResponse.success(trainingPlanService.records(SecurityUtils.currentUser()));
    }

    @GetMapping("/events")
    public ApiResponse<?> myEvents() {
        return ApiResponse.success(skateEventService.myEvents(SecurityUtils.currentUser()));
    }

    @GetMapping("/equipment-favorites")
    public ApiResponse<?> equipmentFavorites() {
        return ApiResponse.success(equipmentService.favorites(SecurityUtils.currentUser()));
    }
}
