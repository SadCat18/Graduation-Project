package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.BizException;
import com.skatehub.util.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicUserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
    private final UserGrowthService userGrowthService = mock(UserGrowthService.class);
    private final PublicUserProfileService service = new PublicUserProfileService(
            userRepository,
            postRepository,
            commentRepository,
            userFollowRepository,
            userGrowthService
    );

    @Test
    void publicProfileReturnsOnlySafeFieldsAndCounts() {
        User user = new User();
        user.setUserId(9L);
        user.setUsername("skater");
        user.setAvatar("/avatar.png");
        user.setGender("1");
        user.setSkateStyle("street");
        user.setBio("keep rolling");
        user.setLevel(4);
        user.setExp(360);
        user.setPhone("13800138000");
        user.setPassword("encoded");
        user.setTokenVersion(7);
        user.setCreateTime(LocalDateTime.of(2026, 6, 1, 12, 0));

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(postRepository.countByUserId(9L)).thenReturn(3L);
        when(userFollowRepository.countByFolloweeIdAndStatus(9L, "1")).thenReturn(8L);
        when(userFollowRepository.countByFollowerIdAndStatus(9L, "1")).thenReturn(5L);

        Map<String, Object> result = service.profile(9L);

        verify(userGrowthService).fillGrowthInfo(user);
        assertThat(result).containsEntry("userId", 9L);
        assertThat(result).containsEntry("username", "skater");
        assertThat(result).containsEntry("postCount", 3L);
        assertThat(result).containsEntry("followerCount", 8L);
        assertThat(result).containsEntry("followingCount", 5L);
        assertThat(result).containsEntry("level", 4);
        assertThat(result).containsEntry("exp", 360);
        assertThat(result).doesNotContainKeys("password", "phone", "tokenVersion");
    }

    @Test
    void publicProfileRejectsMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("user");
    }

    @Test
    void publicPostsReturnPagedSummariesForRequestedUser() {
        Post post = new Post();
        post.setPostId(18L);
        post.setUserId(9L);
        post.setTitle("Skate practice story");
        post.setCategory("experience");
        post.setCreateTime(LocalDateTime.of(2026, 6, 2, 16, 41));
        post.setLikeCount(2);
        post.setCollectCount(1);

        when(userRepository.existsById(9L)).thenReturn(true);
        when(postRepository.findByUserIdOrderByCreateTimeDesc(9L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(commentRepository.countByPostId(18L)).thenReturn(4L);

        PageResult<Map<String, Object>> result = service.posts(9L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0)).containsEntry("postId", 18L);
        assertThat(result.getList().get(0)).containsEntry("commentCount", 4L);
        assertThat(result.getList().get(0)).doesNotContainKeys("content", "images");
    }
}
