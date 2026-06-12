package com.skatehub.controller;

import com.skatehub.pojo.News;
import com.skatehub.pojo.Notice;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.Banner;
import com.skatehub.service.ActivityService;
import com.skatehub.service.PostService;
import com.skatehub.service.PublicContentService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Validated
public class PublicController {

    private final PostService postService;
    private final ActivityService activityService;
    private final PublicContentService publicContentService;

    @GetMapping("/posts")
    public ApiResponse<PageResult<Map<String, Object>>> posts(@RequestParam(required = false) @Size(max = 50) String keyword,
                                                              @RequestParam(required = false) @Size(max = 30) @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9_\\-\\s]*$", message = "分类格式不合法") String category,
                                                              @RequestParam(required = false) @Pattern(regexp = "latest|hot|likes|comments", message = "排序方式不合法") String sort,
                                                              @RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                              @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return ApiResponse.success(postService.list(keyword, category, sort, page, size));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<Map<String, Object>> postDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        return ApiResponse.success(postService.detail(postId));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        return ApiResponse.success(postService.comments(postId));
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<Map<String, Object>>> activities(@RequestParam(required = false) @Min(1) @Max(10000) Integer page,
                                                                   @RequestParam(required = false) @Min(1) @Max(50) Integer size,
                                                                   @RequestParam(required = false) @Size(max = 50) String city,
                                                                   @RequestParam(required = false) @Size(max = 50) String district,
                                                                   @RequestParam(required = false) @Size(max = 50) String keyword,
                                                                   @RequestParam(required = false) Boolean expired) {
        return ApiResponse.success(activityService.list(page, size, null, city, district, keyword, expired));
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<Map<String, Object>> activityDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long activityId) {
        return ApiResponse.success(activityService.publicDetail(activityId));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Notice>> notices() {
        return ApiResponse.success(publicContentService.notices());
    }

    @GetMapping("/bulletins")
    public ApiResponse<List<Map<String, Object>>> bulletins(@RequestParam(required = false) @Min(1) @Max(20) Integer limit) {
        return ApiResponse.success(publicContentService.bulletins(limit));
    }

    @GetMapping("/bulletins/all")
    public ApiResponse<List<Map<String, Object>>> bulletinsAll() {
        return ApiResponse.success(publicContentService.bulletinsAll());
    }

    @GetMapping("/bulletins/{id}")
    public ApiResponse<Map<String, Object>> bulletinDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long bulletinId) {
        return ApiResponse.success(publicContentService.bulletinDetail(bulletinId));
    }

    @GetMapping("/news")
    public ApiResponse<List<News>> news() {
        return ApiResponse.success(publicContentService.news());
    }

    @GetMapping("/news/{id}")
    public ApiResponse<News> newsDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long newsId) {
        return ApiResponse.success(publicContentService.newsDetail(newsId));
    }

    @GetMapping("/places")
    public ApiResponse<List<Place>> places() {
        return ApiResponse.success(publicContentService.places());
    }

    @GetMapping("/places/{id}")
    public ApiResponse<Map<String, Object>> placeDetail(@PathVariable("id") @Positive(message = "id必须为正数") Long placeId) {
        return ApiResponse.success(publicContentService.placeDetail(placeId));
    }

    @GetMapping("/places/{id}/reviews")
    public ApiResponse<List<Map<String, Object>>> placeReviews(@PathVariable("id") @Positive(message = "id必须为正数") Long placeId) {
        return ApiResponse.success(publicContentService.placeReviews(placeId));
    }

    @GetMapping("/videos")
    public ApiResponse<List<Video>> videos() {
        return ApiResponse.success(publicContentService.videos());
    }

    @GetMapping("/banners")
    public ApiResponse<List<Banner>> banners() {
        return ApiResponse.success(publicContentService.banners());
    }
}
