package com.skatehub.controller;

import com.skatehub.config.NewsSyncProperties;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.PageResult;
import com.skatehub.pojo.admin.AdminCommentContextResponse;
import com.skatehub.pojo.admin.AdminCommentResponse;
import com.skatehub.pojo.admin.AdminNewsResponse;
import com.skatehub.pojo.admin.AdminPostDetailResponse;
import com.skatehub.pojo.admin.AdminPostResponse;
import com.skatehub.pojo.admin.CommentBatchDeleteRequest;
import com.skatehub.pojo.admin.NewsAiPreviewRequest;
import com.skatehub.pojo.admin.NewsAiPreviewResponse;
import com.skatehub.pojo.admin.NewsAiReprocessBatchRequest;
import com.skatehub.pojo.admin.NewsAiReprocessBatchResponse;
import com.skatehub.pojo.admin.NewsConfigCheckResponse;
import com.skatehub.pojo.admin.NewsReviewRequest;
import com.skatehub.pojo.admin.NewsSaveRequest;
import com.skatehub.pojo.admin.NewsSyncNowRequest;
import com.skatehub.pojo.admin.NewsSyncTriggerResponse;
import com.skatehub.pojo.admin.NoticeSaveRequest;
import com.skatehub.pojo.admin.PlaceSaveRequest;
import com.skatehub.pojo.admin.PostBatchDeleteRequest;
import com.skatehub.pojo.admin.PostBatchTopRequest;
import com.skatehub.pojo.admin.ActivityReviewRequest;
import com.skatehub.pojo.admin.BannerSaveRequest;
import com.skatehub.pojo.admin.CommunityBulletinReviewRequest;
import com.skatehub.pojo.*;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import com.skatehub.service.ActivityService;
import com.skatehub.service.AdminService;
import com.skatehub.service.NewsAiDiagnosticService;
import com.skatehub.service.NewsFetchService;
import com.skatehub.service.NewsAiReprocessService;
import com.skatehub.service.NewsSyncTaskService;
import com.skatehub.service.PostService;
import com.skatehub.service.ReportService;
import com.skatehub.service.VideoService;
import com.skatehub.service.PlaceReviewService;
import com.skatehub.pojo.report.ReportHandleRequest;
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
    private final NewsFetchService newsFetchService;
    private final NewsAiDiagnosticService newsAiDiagnosticService;
    private final NewsAiReprocessService newsAiReprocessService;
    private final NewsSyncTaskService newsSyncTaskService;
    private final NewsSyncProperties newsSyncProperties;
    private final PostService postService;
    private final ActivityService activityService;
    private final VideoService videoService;
    private final ReportService reportService;
    private final PlaceReviewService placeReviewService;

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
    public ApiResponse<PageResult<AdminPostResponse>> posts(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String top,
                                                            @RequestParam(required = false) String sort) {
        return ApiResponse.success(adminService.listPosts(page, size, keyword, category, top, sort));
    }

    @GetMapping("/posts/{id}/detail")
    public ApiResponse<AdminPostDetailResponse> postDetail(@PathVariable("id") Long postId) {
        return ApiResponse.success(adminService.postDetail(postId));
    }

    @PostMapping("/posts/batch-delete")
    public ApiResponse<Map<String, Object>> batchDeletePosts(@RequestBody PostBatchDeleteRequest request) {
        long deletedCount = adminService.deletePosts(request == null ? List.of() : request.getPostIds());
        return ApiResponse.success(Map.of("deletedCount", deletedCount));
    }

    @PostMapping("/posts/batch-top")
    public ApiResponse<Map<String, Object>> batchTopPosts(@RequestBody PostBatchTopRequest request) {
        long updatedCount = adminService.topPosts(
                request == null ? List.of() : request.getPostIds(),
                request == null ? "0" : request.getTop()
        );
        return ApiResponse.success(Map.of("updatedCount", updatedCount));
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
    public ApiResponse<PageResult<AdminCommentResponse>> comments(@RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Long postId,
                                                                  @RequestParam(required = false) Long userId,
                                                                  @RequestParam(required = false) String type,
                                                                  @RequestParam(required = false) String sort) {
        return ApiResponse.success(adminService.listComments(page, size, keyword, postId, userId, type, sort));
    }

    @GetMapping("/comments/{id}/context")
    public ApiResponse<AdminCommentContextResponse> commentContext(@PathVariable("id") Long commentId) {
        return ApiResponse.success(adminService.commentContext(commentId));
    }

    @PostMapping("/comments/batch-delete")
    public ApiResponse<Map<String, Object>> batchDeleteComments(@RequestBody CommentBatchDeleteRequest request) {
        long deletedCount = adminService.deleteComments(request == null ? List.of() : request.getCommentIds());
        return ApiResponse.success(Map.of("deletedCount", deletedCount));
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
    public ApiResponse<List<Map<String, Object>>> bulletins(@RequestParam(required = false) String type,
                                                            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.listCommunityBulletins(type, status));
    }

    @GetMapping("/bulletins/type-stats")
    public ApiResponse<List<Map<String, Object>>> bulletinTypeStats() {
        return ApiResponse.success(adminService.communityBulletinTypeStats());
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
    public ApiResponse<List<AdminNewsResponse>> news() {
        return ApiResponse.success(adminService.listNews());
    }

    @GetMapping("/news/{id}")
    public ApiResponse<AdminNewsResponse> newsDetail(@PathVariable("id") Long newsId) {
        return ApiResponse.success(adminService.newsDetail(newsId));
    }

    @PostMapping("/news")
    public ApiResponse<News> createNews(@RequestBody @Valid NewsSaveRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(adminService.createNews(currentUser, request));
    }

    @PostMapping("/news/sync")
    public ApiResponse<NewsSyncTriggerResponse> syncNews() {
        return ApiResponse.success(newsSyncTaskService.startAsync(new NewsSyncNowRequest()));
    }

    @PostMapping("/news/ai-sync")
    public ApiResponse<NewsSyncTriggerResponse> syncNewsAi() {
        NewsSyncNowRequest request = new NewsSyncNowRequest();
        Integer sourceId = newsSyncProperties.getAiSearch() == null
                ? 999
                : newsSyncProperties.getAiSearch().getSourceId();
        request.setSourceId(sourceId == null ? 999 : sourceId);
        return ApiResponse.success(newsSyncTaskService.startAsync(request));
    }

    @GetMapping("/news/sync-status")
    public ApiResponse<NewsSyncTriggerResponse> syncNewsStatus() {
        return ApiResponse.success(newsSyncTaskService.currentStatus());
    }

    @PostMapping("/news/sync-now")
    public ApiResponse<NewsSyncTriggerResponse> syncNewsNow(
            @RequestBody(required = false) NewsSyncNowRequest request) {
        return ApiResponse.success(newsFetchService.syncNow(request == null ? new NewsSyncNowRequest() : request));
    }

    @PutMapping("/news/{id}")
    public ApiResponse<News> updateNews(@PathVariable("id") Long newsId,
                                        @RequestBody @Valid NewsSaveRequest request) {
        return ApiResponse.success(adminService.updateNews(newsId, request));
    }

    @PutMapping("/news/{id}/review")
    public ApiResponse<News> reviewNews(@PathVariable("id") Long newsId,
                                        @RequestBody @Valid NewsReviewRequest request) {
        return ApiResponse.success(adminService.reviewNews(newsId, request.getStatus()));
    }

    @GetMapping("/ai/news-config-check")
    public ApiResponse<NewsConfigCheckResponse> newsConfigCheck() {
        return ApiResponse.success(newsAiDiagnosticService.checkNewsConfig());
    }

    @PostMapping("/news/ai-preview")
    public ApiResponse<NewsAiPreviewResponse> newsAiPreview(
            @RequestBody(required = false) NewsAiPreviewRequest request) {
        return ApiResponse.success(newsAiDiagnosticService.preview(request == null ? new NewsAiPreviewRequest() : request));
    }

    @PostMapping("/news/{id}/ai-reprocess")
    public ApiResponse<AdminNewsResponse> reprocessNewsAi(@PathVariable("id") Long newsId) {
        return ApiResponse.success(newsAiReprocessService.reprocessSingle(newsId));
    }

    @PostMapping("/news/ai-reprocess/batch")
    public ApiResponse<NewsAiReprocessBatchResponse> reprocessNewsAiBatch(
            @RequestBody(required = false) NewsAiReprocessBatchRequest request) {
        return ApiResponse.success(newsAiReprocessService.reprocessBatch(
                request == null ? null : request.getNewsIds()
        ));
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

    @GetMapping("/analytics")
    public ApiResponse<Map<String, Object>> analytics() {
        return ApiResponse.success(adminService.operationAnalytics());
    }

    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> reports() {
        return ApiResponse.success(reportService.adminList());
    }

    @PutMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(@PathVariable("id") Long reportId,
                                          @RequestBody @Valid ReportHandleRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        reportService.handle(reportId, currentUser, request.getStatus(), request.getHandleNote());
        return ApiResponse.success();
    }

    @DeleteMapping("/reports/{id}/target")
    public ApiResponse<Void> deleteReportedTarget(@PathVariable("id") Long reportId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        reportService.deleteReportedTarget(reportId, currentUser);
        return ApiResponse.success();
    }

    @GetMapping("/place-reviews")
    public ApiResponse<List<Map<String, Object>>> placeReviews() {
        return ApiResponse.success(placeReviewService.adminReviews());
    }

    @DeleteMapping("/place-reviews/{id}")
    public ApiResponse<Void> deletePlaceReview(@PathVariable("id") Long reviewId) {
        placeReviewService.adminDeleteReview(reviewId);
        return ApiResponse.success();
    }
}
