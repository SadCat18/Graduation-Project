package com.javademo1.controller;

import com.javademo1.pojo.CommunityBulletin;
import com.javademo1.pojo.community.CommunityBulletinCreateRequest;
import com.javademo1.service.CommunityBulletinService;
import com.javademo1.util.ApiResponse;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.SecurityUtils;
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
