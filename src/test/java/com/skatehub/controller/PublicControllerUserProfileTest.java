package com.skatehub.controller;

import com.skatehub.service.ActivityService;
import com.skatehub.service.PostService;
import com.skatehub.service.PublicContentService;
import com.skatehub.service.PublicUserProfileService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicControllerUserProfileTest {

    private final PostService postService = mock(PostService.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final PublicContentService publicContentService = mock(PublicContentService.class);
    private final PublicUserProfileService publicUserProfileService = mock(PublicUserProfileService.class);
    private final PublicController controller = new PublicController(
            postService,
            activityService,
            publicContentService,
            publicUserProfileService
    );

    @Test
    void publicUserProfileReturnsServicePayload() {
        Map<String, Object> profile = Map.of("userId", 9L, "username", "skater");
        when(publicUserProfileService.profile(9L)).thenReturn(profile);

        ApiResponse<Map<String, Object>> response = controller.publicUserProfile(9L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(profile);
        verify(publicUserProfileService).profile(9L);
    }

    @Test
    void publicUserPostsReturnsServicePayload() {
        PageResult<Map<String, Object>> posts = new PageResult<>(1, List.of(Map.of("postId", 18L)));
        when(publicUserProfileService.posts(9L, 1, 10)).thenReturn(posts);

        ApiResponse<PageResult<Map<String, Object>>> response = controller.publicUserPosts(9L, 1, 10);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(posts);
        verify(publicUserProfileService).posts(9L, 1, 10);
    }
}
