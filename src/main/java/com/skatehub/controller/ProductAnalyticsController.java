package com.skatehub.controller;

import com.skatehub.pojo.analytics.ProductEventRequest;
import com.skatehub.service.ProductAnalyticsService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-events")
@RequiredArgsConstructor
public class ProductAnalyticsController {

    private final ProductAnalyticsService productAnalyticsService;

    @PostMapping
    public ApiResponse<Void> record(@RequestBody @Valid ProductEventRequest request) {
        productAnalyticsService.record(SecurityUtils.currentUser(), request);
        return ApiResponse.success();
    }
}
