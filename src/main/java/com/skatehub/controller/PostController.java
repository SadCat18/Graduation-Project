package com.skatehub.controller;

import com.skatehub.util.ApiResponse;
import com.skatehub.pojo.comment.CommentCreateRequest;
import com.skatehub.pojo.post.PostCreateRequest;
import com.skatehub.pojo.post.PostUpdateRequest;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Post;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import com.skatehub.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;

    @PostMapping("/posts")
    public ApiResponse<Post> create(@RequestBody @Valid PostCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.create(currentUser, request));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<Post> update(@PathVariable("id") @Positive(message = "id必须为正数") Long postId,
                                    @RequestBody @Valid PostUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.update(currentUser, postId, request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.delete(currentUser, postId, false);
        return ApiResponse.success();
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<Comment> comment(@PathVariable("id") @Positive(message = "id必须为正数") Long postId,
                                        @RequestBody @Valid CommentCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.addComment(currentUser, postId, request));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable("id") @Positive(message = "id必须为正数") Long commentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.deleteComment(currentUser, commentId, false);
        return ApiResponse.success();
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.toggleLike(currentUser, postId));
    }

    @PostMapping("/posts/{id}/collect")
    public ApiResponse<Map<String, Object>> collect(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.toggleCollect(currentUser, postId));
    }

    @PostMapping("/posts/{id}/watch-later")
    public ApiResponse<Map<String, Object>> watchLater(@PathVariable("id") @Positive(message = "id必须为正数") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.toggleWatchLater(currentUser, postId));
    }
}
