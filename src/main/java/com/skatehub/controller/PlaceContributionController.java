package com.skatehub.controller;

import com.skatehub.pojo.Place;
import com.skatehub.pojo.place.PlaceContributionRequest;
import com.skatehub.pojo.place.PlaceContributionReviewRequest;
import com.skatehub.service.PlaceContributionService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class PlaceContributionController {

    private final PlaceContributionService placeContributionService;

    @PostMapping("/user/places")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Place> submit(@RequestBody @Valid PlaceContributionRequest request) {
        return ApiResponse.success(placeContributionService.submit(SecurityUtils.currentUser(), request));
    }

    @GetMapping("/admin/places/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Place>> pending() {
        return ApiResponse.success(placeContributionService.pending());
    }

    @PutMapping("/admin/places/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Place> review(@PathVariable("id") @Positive Long placeId,
                                     @RequestBody @Valid PlaceContributionReviewRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(placeContributionService.review(placeId, currentUser, request));
    }
}
