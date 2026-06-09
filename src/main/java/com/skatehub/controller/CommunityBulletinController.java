package com.skatehub.controller;

import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.community.CommunityBulletinCreateRequest;
import com.skatehub.service.CommunityBulletinService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bulletins")
@RequiredArgsConstructor
public class CommunityBulletinController {

    private final CommunityBulletinService communityBulletinService;

    @PostMapping
    public ApiResponse<CommunityBulletin> create(@RequestBody @Valid CommunityBulletinCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(communityBulletinService.create(currentUser, request));
    }
}
