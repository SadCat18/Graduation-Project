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
import com.skatehub.service.AdminAuditLogService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
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
    private final AdminAuditLogService adminAuditLogService;

    @GetMapping("/users")
    public ApiResponse<PageResult<User>> users(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                               @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return ApiResponse.success(adminService.listUsers(page, size));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<User> updateUserStatus(@PathVariable("id") @Positive(message = "id必须为正数") Long userId,
                                              @RequestParam @Pattern(regexp = "[01]", message = "状态值不合法") String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "USER", userId, "UPDATE_STATUS",
                () -> adminService.updateUserStatus(userId, status)));
    }

    @PutMapping("/users/{id}/bulletin-permission")
    public ApiResponse<User> updateUserBulletinPermission(@PathVariable("id") @Positive(message = "id必须为正数") Long userId,
                                                          @RequestParam @Pattern(regexp = "[01]", message = "权限值不合法") String permission) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "USER", userId, "UPDATE_PERMISSION",
                () -> adminService.updateUserBulletinPermission(userId, permission)));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<AdminPostResponse>> posts(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                            @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                            @RequestParam(required = false) @Size(max = 50) String keyword,
                                                            @RequestParam(required = false) @Size(max = 30) @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9_\\-\\s]*$", message = "分类格式不合法") String category,
                                                            @RequestParam(required = false) @Pattern(regexp = "all|top|normal", message = "置顶筛选值不合法") String top,
                                                            @RequestParam(required = false) @Pattern(regexp = "latest|likes|comments|collects", message = "排序方式不合法") String sort) {
        return ApiResponse.success(adminService.listPosts(page, size, keyword, category, top, sort));
    }

    @GetMapping("/posts/{id}/detail")
    public ApiResponse<AdminPostDetailResponse> postDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        return ApiResponse.success(adminService.postDetail(postId));
    }

    @PostMapping("/posts/batch-delete")
    public ApiResponse<Map<String, Object>> batchDeletePosts(@RequestBody @Valid PostBatchDeleteRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        List<Long> ids = request == null ? List.of() : request.getPostIds();
        long deletedCount = audit(currentUser, "POST_BATCH", null, "DELETE", () -> adminService.deletePosts(ids));
        ids.forEach(id -> adminAuditLogService.record(currentUser.id(), "POST", id, "DELETE", "SUCCESS", "batch"));
        return ApiResponse.success(Map.of("deletedCount", deletedCount));
    }

    @PostMapping("/posts/batch-top")
    public ApiResponse<Map<String, Object>> batchTopPosts(@RequestBody @Valid PostBatchTopRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        List<Long> ids = request == null ? List.of() : request.getPostIds();
        long updatedCount = audit(currentUser, "POST_BATCH", null, "TOP",
                () -> adminService.topPosts(ids, request == null ? "0" : request.getTop()));
        ids.forEach(id -> adminAuditLogService.record(currentUser.id(), "POST", id, "TOP", "SUCCESS", "batch"));
        return ApiResponse.success(Map.of("updatedCount", updatedCount));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "POST", postId, "DELETE", () -> postService.delete(currentUser, postId, true));
        return ApiResponse.success();
    }

    @PutMapping("/posts/{id}/top")
    public ApiResponse<Post> top(@PathVariable("id") @Positive(message = "id必须为正数") Long postId,
                                 @RequestParam @Pattern(regexp = "[01]", message = "置顶值不合法") String top) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "POST", postId, "TOP", () -> postService.top(postId, top)));
    }

    @GetMapping("/comments")
    public ApiResponse<PageResult<AdminCommentResponse>> comments(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                                  @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                                  @RequestParam(required = false) @Size(max = 50) String keyword,
                                                                  @RequestParam(required = false) @Positive(message = "postId必须为正数") Long postId,
                                                                  @RequestParam(required = false) @Positive(message = "userId必须为正数") Long userId,
                                                                  @RequestParam(required = false) @Pattern(regexp = "all|root|reply", message = "评论类型不合法") String type,
                                                                  @RequestParam(required = false) @Pattern(regexp = "latest|oldest", message = "排序方式不合法") String sort) {
        return ApiResponse.success(adminService.listComments(page, size, keyword, postId, userId, type, sort));
    }

    @GetMapping("/comments/{id}/context")
    public ApiResponse<AdminCommentContextResponse> commentContext(@PathVariable("id") @Positive(message = "id必须为正数") Long commentId) {
        return ApiResponse.success(adminService.commentContext(commentId));
    }

    @PostMapping("/comments/batch-delete")
    public ApiResponse<Map<String, Object>> batchDeleteComments(@RequestBody @Valid CommentBatchDeleteRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        List<Long> ids = request == null ? List.of() : request.getCommentIds();
        long deletedCount = audit(currentUser, "COMMENT_BATCH", null, "DELETE", () -> adminService.deleteComments(ids));
        ids.forEach(id -> adminAuditLogService.record(currentUser.id(), "COMMENT", id, "DELETE", "SUCCESS", "batch"));
        return ApiResponse.success(Map.of("deletedCount", deletedCount));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable("id") @Positive(message = "id必须为正数") Long commentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "COMMENT", commentId, "DELETE", () -> postService.deleteComment(currentUser, commentId, true));
        return ApiResponse.success();
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<Activity>> activities(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                        @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                        @RequestParam(required = false) @Pattern(regexp = "[012]", message = "审核状态不合法") String reviewStatus) {
        return ApiResponse.success(adminService.listActivities(page, size, reviewStatus));
    }

    @PutMapping("/activities/{id}/status")
    public ApiResponse<Activity> updateActivityStatus(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId,
                                                      @RequestParam @Pattern(regexp = "[0-5]", message = "活动状态不合法") String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "ACTIVITY", activityId, "UPDATE_STATUS",
                () -> activityService.updateStatus(activityId, status)));
    }

    @PutMapping("/activities/{id}/review")
    public ApiResponse<Activity> reviewActivity(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId,
                                                @RequestBody @Valid ActivityReviewRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "ACTIVITY", activityId, "REVIEW",
                () -> activityService.review(activityId, request.getStatus())));
    }

    @DeleteMapping("/activities/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "ACTIVITY", activityId, "DELETE", () -> activityService.delete(currentUser, activityId, true));
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
    public ApiResponse<Notice> updateNotice(@PathVariable("id") @Positive(message = "id必须为正数") Long noticeId,
                                            @RequestBody @Valid NoticeSaveRequest request) {
        return ApiResponse.success(adminService.updateNotice(noticeId, request));
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Void> deleteNotice(@PathVariable("id") @Positive(message = "id必须为正数") Long noticeId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "NOTICE", noticeId, "DELETE", () -> adminService.deleteNotice(noticeId));
        return ApiResponse.success();
    }

    @GetMapping("/bulletins")
    public ApiResponse<List<Map<String, Object>>> bulletins(@RequestParam(required = false) @Size(max = 30) String type,
                                                            @RequestParam(required = false) @Pattern(regexp = "[012]", message = "状态值不合法") String status) {
        return ApiResponse.success(adminService.listCommunityBulletins(type, status));
    }

    @GetMapping("/bulletins/type-stats")
    public ApiResponse<List<Map<String, Object>>> bulletinTypeStats() {
        return ApiResponse.success(adminService.communityBulletinTypeStats());
    }

    @PutMapping("/bulletins/{id}/review")
    public ApiResponse<CommunityBulletin> reviewBulletin(@PathVariable("id") @Positive(message = "id必须为正数") Long bulletinId,
                                                          @RequestBody @Valid CommunityBulletinReviewRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "BULLETIN", bulletinId, "REVIEW",
                () -> adminService.reviewCommunityBulletin(bulletinId, currentUser, request)));
    }

    @DeleteMapping("/bulletins/{id}")
    public ApiResponse<Void> deleteBulletin(@PathVariable("id") @Positive(message = "id必须为正数") Long bulletinId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "BULLETIN", bulletinId, "DELETE", () -> adminService.deleteCommunityBulletin(bulletinId));
        return ApiResponse.success();
    }

    @GetMapping("/news")
    public ApiResponse<List<AdminNewsResponse>> news() {
        return ApiResponse.success(adminService.listNews());
    }

    @GetMapping("/news/{id}")
    public ApiResponse<AdminNewsResponse> newsDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId) {
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
            @RequestBody(required = false) @Valid NewsSyncNowRequest request) {
        return ApiResponse.success(newsFetchService.syncNow(request == null ? new NewsSyncNowRequest() : request));
    }

    @PutMapping("/news/{id}")
    public ApiResponse<News> updateNews(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId,
                                        @RequestBody @Valid NewsSaveRequest request) {
        return ApiResponse.success(adminService.updateNews(newsId, request));
    }

    @PutMapping("/news/{id}/review")
    public ApiResponse<News> reviewNews(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId,
                                        @RequestBody @Valid NewsReviewRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(audit(currentUser, "NEWS", newsId, "REVIEW",
                () -> adminService.reviewNews(newsId, request.getStatus())));
    }

    @GetMapping("/ai/news-config-check")
    public ApiResponse<NewsConfigCheckResponse> newsConfigCheck() {
        return ApiResponse.success(newsAiDiagnosticService.checkNewsConfig());
    }

    @PostMapping("/news/ai-preview")
    public ApiResponse<NewsAiPreviewResponse> newsAiPreview(
            @RequestBody(required = false) @Valid NewsAiPreviewRequest request) {
        return ApiResponse.success(newsAiDiagnosticService.preview(request == null ? new NewsAiPreviewRequest() : request));
    }

    @PostMapping("/news/{id}/ai-reprocess")
    public ApiResponse<AdminNewsResponse> reprocessNewsAi(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId) {
        return ApiResponse.success(newsAiReprocessService.reprocessSingle(newsId));
    }

    @PostMapping("/news/ai-reprocess/batch")
    public ApiResponse<NewsAiReprocessBatchResponse> reprocessNewsAiBatch(
            @RequestBody(required = false) @Valid NewsAiReprocessBatchRequest request) {
        return ApiResponse.success(newsAiReprocessService.reprocessBatch(
                request == null ? null : request.getNewsIds()
        ));
    }

    @DeleteMapping("/news/{id}")
    public ApiResponse<Void> deleteNews(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "NEWS", newsId, "DELETE", () -> adminService.deleteNews(newsId));
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
    public ApiResponse<Place> updatePlace(@PathVariable("id") @Positive(message = "id必须为正数") Long placeId,
                                          @RequestBody @Valid PlaceSaveRequest request) {
        return ApiResponse.success(adminService.updatePlace(placeId, request));
    }

    @DeleteMapping("/places/{id}")
    public ApiResponse<Void> deletePlace(@PathVariable("id") @Positive(message = "id必须为正数") Long placeId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "PLACE", placeId, "DELETE", () -> adminService.deletePlace(placeId));
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
    public ApiResponse<Banner> updateBanner(@PathVariable("id") @Positive(message = "id必须为正数") Long bannerId,
                                            @RequestBody @Valid BannerSaveRequest request) {
        return ApiResponse.success(adminService.updateBanner(bannerId, request));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable("id") @Positive(message = "id必须为正数") Long bannerId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "BANNER", bannerId, "DELETE", () -> adminService.deleteBanner(bannerId));
        return ApiResponse.success();
    }

    @DeleteMapping("/videos/{id}")
    public ApiResponse<Void> deleteVideo(@PathVariable("id") @Positive(message = "id必须为正数") Long videoId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "VIDEO", videoId, "DELETE", () -> videoService.delete(currentUser, videoId, true));
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

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLog>> auditLogs() {
        return ApiResponse.success(adminAuditLogService.list());
    }

    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> reports() {
        return ApiResponse.success(reportService.adminList());
    }

    @PutMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(@PathVariable("id") @Positive(message = "id必须为正数") Long reportId,
                                          @RequestBody @Valid ReportHandleRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "REPORT", reportId, "HANDLE",
                () -> reportService.handle(reportId, currentUser, request.getStatus(), request.getHandleNote()));
        return ApiResponse.success();
    }

    @DeleteMapping("/reports/{id}/target")
    public ApiResponse<Void> deleteReportedTarget(@PathVariable("id") @Positive(message = "id必须为正数") Long reportId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "REPORT", reportId, "DELETE_TARGET",
                () -> reportService.deleteReportedTarget(reportId, currentUser));
        return ApiResponse.success();
    }

    @GetMapping("/place-reviews")
    public ApiResponse<List<Map<String, Object>>> placeReviews() {
        return ApiResponse.success(placeReviewService.adminReviews());
    }

    @DeleteMapping("/place-reviews/{id}")
    public ApiResponse<Void> deletePlaceReview(@PathVariable("id") @Positive(message = "id必须为正数") Long reviewId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        auditVoid(currentUser, "PLACE_REVIEW", reviewId, "DELETE", () -> placeReviewService.adminDeleteReview(reviewId));
        return ApiResponse.success();
    }
    private <T> T audit(CurrentUser currentUser, String targetType, Long targetId, String action, Supplier<T> operation) {
        try {
            T result = operation.get();
            adminAuditLogService.record(currentUser.id(), targetType, targetId, action, "SUCCESS");
            return result;
        } catch (RuntimeException exception) {
            adminAuditLogService.record(currentUser.id(), targetType, targetId, action, "FAILURE", exception.getMessage());
            throw exception;
        }
    }

    private void auditVoid(CurrentUser currentUser, String targetType, Long targetId, String action, Runnable operation) {
        audit(currentUser, targetType, targetId, action, () -> {
            operation.run();
            return null;
        });
    }
}
