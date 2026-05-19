package com.javademo1.controller;

import com.javademo1.pojo.News;
import com.javademo1.pojo.Notice;
import com.javademo1.pojo.Place;
import com.javademo1.pojo.Video;
import com.javademo1.pojo.Banner;
import com.javademo1.service.ActivityService;
import com.javademo1.service.PostService;
import com.javademo1.service.PublicContentService;
import com.javademo1.util.ApiResponse;
import com.javademo1.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PostService postService;
    private final ActivityService activityService;
    private final PublicContentService publicContentService;

    @GetMapping("/posts")
    public ApiResponse<PageResult<Map<String, Object>>> posts(@RequestParam(required = false) String category,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.success(postService.list(category, page, size));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<Map<String, Object>> postDetail(@PathVariable("id") Long postId) {
        return ApiResponse.success(postService.detail(postId));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@PathVariable("id") Long postId) {
        return ApiResponse.success(postService.comments(postId));
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<Map<String, Object>>> activities(@RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer size,
                                                                   @RequestParam(required = false) String city,
                                                                   @RequestParam(required = false) String district,
                                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.success(activityService.list(page, size, null, city, district, keyword));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Notice>> notices() {
        return ApiResponse.success(publicContentService.notices());
    }

    @GetMapping("/bulletins")
    public ApiResponse<List<Map<String, Object>>> bulletins(@RequestParam(required = false) Integer limit) {
        return ApiResponse.success(publicContentService.bulletins(limit));
    }

    @GetMapping("/bulletins/all")
    public ApiResponse<List<Map<String, Object>>> bulletinsAll() {
        return ApiResponse.success(publicContentService.bulletinsAll());
    }

    @GetMapping("/bulletins/{id}")
    public ApiResponse<Map<String, Object>> bulletinDetail(@PathVariable("id") Long bulletinId) {
        return ApiResponse.success(publicContentService.bulletinDetail(bulletinId));
    }

    @GetMapping("/news")
    public ApiResponse<List<News>> news() {
        return ApiResponse.success(publicContentService.news());
    }

    @GetMapping("/news/{id}")
    public ApiResponse<News> newsDetail(@PathVariable("id") Long newsId) {
        return ApiResponse.success(publicContentService.newsDetail(newsId));
    }

    @GetMapping("/places")
    public ApiResponse<List<Place>> places() {
        return ApiResponse.success(publicContentService.places());
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
