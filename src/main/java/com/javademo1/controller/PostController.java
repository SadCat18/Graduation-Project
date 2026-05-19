package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.pojo.comment.CommentCreateRequest;
import com.javademo1.pojo.post.PostCreateRequest;
import com.javademo1.pojo.post.PostUpdateRequest;
import com.javademo1.pojo.Comment;
import com.javademo1.pojo.Post;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.SecurityUtils;
import com.javademo1.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/posts")
    public ApiResponse<Post> create(@RequestBody @Valid PostCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.create(currentUser, request));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<Post> update(@PathVariable("id") Long postId,
                                    @RequestBody @Valid PostUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.update(currentUser, postId, request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.delete(currentUser, postId, false);
        return ApiResponse.success();
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<Comment> comment(@PathVariable("id") Long postId,
                                        @RequestBody @Valid CommentCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.addComment(currentUser, postId, request));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable("id") Long commentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        postService.deleteComment(currentUser, commentId, false);
        return ApiResponse.success();
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable("id") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.toggleLike(currentUser, postId));
    }

    @PostMapping("/posts/{id}/collect")
    public ApiResponse<Map<String, Object>> collect(@PathVariable("id") Long postId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(postService.toggleCollect(currentUser, postId));
    }
}

