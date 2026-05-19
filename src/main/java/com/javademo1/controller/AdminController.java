package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.util.PageResult;
import com.javademo1.pojo.admin.NewsSaveRequest;
import com.javademo1.pojo.admin.NoticeSaveRequest;
import com.javademo1.pojo.admin.PlaceSaveRequest;
import com.javademo1.pojo.admin.ActivityReviewRequest;
import com.javademo1.pojo.admin.BannerSaveRequest;
import com.javademo1.pojo.admin.CommunityBulletinReviewRequest;
import com.javademo1.pojo.*;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.SecurityUtils;
import com.javademo1.service.ActivityService;
import com.javademo1.service.AdminService;
import com.javademo1.service.PostService;
import com.javademo1.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final PostService postService;
    private final ActivityService activityService;
    private final VideoService videoService;

    @GetMapping("/users")
    public ApiResponse<PageResult<User>> users(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.success(adminService.listUsers(page, size));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<User> updateUserStatus(@PathVariable("id") Long userId, @RequestParam String status) {
        return ApiResponse.success(adminService.updateUserStatus(userId, status));
    }

    @PutMapping("/users/{id}/bulletin-permission")
    public ApiResponse<User> updateUserBulletinPermission(@PathVariable("id") Long userId, @RequestParam String permission) {
        return ApiResponse.success(adminService.updateUserBulletinPermission(userId, permission));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<Post>> posts(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.success(adminService.listPosts(page, size));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable("id") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.delete(currentUser, postId, true);
        return ApiResponse.success();
    }

    @PutMapping("/posts/{id}/top")
    public ApiResponse<Post> top(@PathVariable("id") Long postId, @RequestParam String top) {
        return ApiResponse.success(postService.top(postId, top));
    }

    @GetMapping("/comments")
    public ApiResponse<PageResult<Comment>> comments(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.success(adminService.listComments(page, size));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable("id") Long commentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.deleteComment(currentUser, commentId, true);
        return ApiResponse.success();
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<Activity>> activities(@RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String reviewStatus) {
        return ApiResponse.success(adminService.listActivities(page, size, reviewStatus));
    }

    @PutMapping("/activities/{id}/status")
    public ApiResponse<Activity> updateActivityStatus(@PathVariable("id") Long activityId,
                                                      @RequestParam String status) {
        return ApiResponse.success(activityService.updateStatus(activityId, status));
    }

    @PutMapping("/activities/{id}/review")
    public ApiResponse<Activity> reviewActivity(@PathVariable("id") Long activityId,
                                                @RequestBody @Valid ActivityReviewRequest request) {
        return ApiResponse.success(activityService.review(activityId, request.getStatus()));
    }

    @DeleteMapping("/activities/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable("id") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        activityService.delete(currentUser, activityId, true);
        return ApiResponse.success();
    }

    @GetMapping("/notices")
    public ApiResponse<List<Notice>> notices() {
        return ApiResponse.success(adminService.listNotices());
    }

    @PostMapping("/notices")
    public ApiResponse<Notice> createNotice(@RequestBody @Valid NoticeSaveRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(adminService.createNotice(currentUser, request));
    }

    @PutMapping("/notices/{id}")
    public ApiResponse<Notice> updateNotice(@PathVariable("id") Long noticeId,
                                            @RequestBody @Valid NoticeSaveRequest request) {
        return ApiResponse.success(adminService.updateNotice(noticeId, request));
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Void> deleteNotice(@PathVariable("id") Long noticeId) {
        adminService.deleteNotice(noticeId);
        return ApiResponse.success();
    }

    @GetMapping("/bulletins")
    public ApiResponse<List<Map<String, Object>>> bulletins() {
        return ApiResponse.success(adminService.listCommunityBulletins());
    }

    @PutMapping("/bulletins/{id}/review")
    public ApiResponse<CommunityBulletin> reviewBulletin(@PathVariable("id") Long bulletinId,
                                                          @RequestBody @Valid CommunityBulletinReviewRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(adminService.reviewCommunityBulletin(bulletinId, currentUser, request));
    }

    @DeleteMapping("/bulletins/{id}")
    public ApiResponse<Void> deleteBulletin(@PathVariable("id") Long bulletinId) {
        adminService.deleteCommunityBulletin(bulletinId);
        return ApiResponse.success();
    }

    @GetMapping("/news")
    public ApiResponse<List<News>> news() {
        return ApiResponse.success(adminService.listNews());
    }

    @PostMapping("/news")
    public ApiResponse<News> createNews(@RequestBody @Valid NewsSaveRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(adminService.createNews(currentUser, request));
    }

    @PutMapping("/news/{id}")
    public ApiResponse<News> updateNews(@PathVariable("id") Long newsId,
                                        @RequestBody @Valid NewsSaveRequest request) {
        return ApiResponse.success(adminService.updateNews(newsId, request));
    }

    @DeleteMapping("/news/{id}")
    public ApiResponse<Void> deleteNews(@PathVariable("id") Long newsId) {
        adminService.deleteNews(newsId);
        return ApiResponse.success();
    }

    @GetMapping("/places")
    public ApiResponse<List<Place>> places() {
        return ApiResponse.success(adminService.listPlaces());
    }

    @PostMapping("/places")
    public ApiResponse<Place> createPlace(@RequestBody @Valid PlaceSaveRequest request) {
        return ApiResponse.success(adminService.createPlace(request));
    }

    @PutMapping("/places/{id}")
    public ApiResponse<Place> updatePlace(@PathVariable("id") Long placeId,
                                          @RequestBody @Valid PlaceSaveRequest request) {
        return ApiResponse.success(adminService.updatePlace(placeId, request));
    }

    @DeleteMapping("/places/{id}")
    public ApiResponse<Void> deletePlace(@PathVariable("id") Long placeId) {
        adminService.deletePlace(placeId);
        return ApiResponse.success();
    }

    @GetMapping("/videos")
    public ApiResponse<List<Video>> videos() {
        return ApiResponse.success(adminService.listVideos());
    }

    @GetMapping("/banners")
    public ApiResponse<List<Banner>> banners() {
        return ApiResponse.success(adminService.listBanners());
    }

    @PostMapping("/banners")
    public ApiResponse<Banner> createBanner(@RequestBody @Valid BannerSaveRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(adminService.createBanner(currentUser, request));
    }

    @PutMapping("/banners/{id}")
    public ApiResponse<Banner> updateBanner(@PathVariable("id") Long bannerId,
                                            @RequestBody @Valid BannerSaveRequest request) {
        return ApiResponse.success(adminService.updateBanner(bannerId, request));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable("id") Long bannerId) {
        adminService.deleteBanner(bannerId);
        return ApiResponse.success();
    }

    @DeleteMapping("/videos/{id}")
    public ApiResponse<Void> deleteVideo(@PathVariable("id") Long videoId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        videoService.delete(currentUser, videoId, true);
        return ApiResponse.success();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(adminService.stats());
    }
}

