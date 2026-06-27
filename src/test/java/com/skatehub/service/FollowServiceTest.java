package com.skatehub.service;

import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserFollow;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private UserFollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserGrowthService userGrowthService;
    @Mock private MessageNotifyService messageNotifyService;

    private FollowService service;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        service = new FollowService(followRepository, userRepository, userGrowthService, messageNotifyService);
        currentUser = new CurrentUser(1L, "USER", "skater");
    }

    @Test
    void followRejectsSelfFollow() {
        assertThatThrownBy(() -> service.follow(currentUser, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("自己");
    }

    @Test
    void firstFollowCreatesActiveRelationAndAwardsGrowth() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bowl")));
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(UserFollow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.follow(currentUser, 2L);

        assertThat(result).containsEntry("following", true);
        verify(userGrowthService).grantExp(1L, "USER_FOLLOW", "USER", 2L, 2);
        verify(messageNotifyService).send(2L, "FOLLOW", "skater 关注了你", "USER", 1L);
    }

    @Test
    void duplicateActiveFollowIsIdempotent() {
        UserFollow existing = follow(1L, 2L, "1");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bowl")));
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.follow(currentUser, 2L);

        assertThat(result).containsEntry("following", true);
        verify(followRepository, never()).save(any());
        verify(userGrowthService, never()).grantExp(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void canceledFollowCanReactivate() {
        UserFollow existing = follow(1L, 2L, "0");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bowl")));
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(existing));

        service.follow(currentUser, 2L);

        assertThat(existing.getStatus()).isEqualTo("1");
        verify(followRepository).save(existing);
    }

    @Test
    void unfollowChangesStatus() {
        UserFollow existing = follow(1L, 2L, "1");
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.unfollow(currentUser, 2L);

        assertThat(result).containsEntry("following", false);
        assertThat(existing.getStatus()).isEqualTo("0");
        verify(followRepository).save(existing);
    }

    @Test
    void statusIncludesCountsAndMutualRelationship() {
        when(followRepository.existsByFollowerIdAndFolloweeIdAndStatus(1L, 2L, "1")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeIdAndStatus(2L, 1L, "1")).thenReturn(true);
        when(followRepository.countByFollowerIdAndStatus(2L, "1")).thenReturn(5L);
        when(followRepository.countByFolloweeIdAndStatus(2L, "1")).thenReturn(8L);

        Map<String, Object> status = service.status(currentUser, 2L);

        assertThat(status).containsEntry("following", true)
                .containsEntry("mutual", true)
                .containsEntry("followingCount", 5L)
                .containsEntry("followerCount", 8L);
    }

    @Test
    void followingListReturnsSafeUserCards() {
        UserFollow row = follow(1L, 2L, "1");
        row.setCreateTime(LocalDateTime.parse("2026-06-27T10:00:00"));
        User user = user(2L, "bowl");
        user.setPhone("13800000000");
        when(followRepository.findByFollowerIdAndStatusOrderByCreateTimeDesc(1L, "1")).thenReturn(List.of(row));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user));

        List<Map<String, Object>> rows = service.following(1L);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("userId", 2L).containsEntry("username", "bowl");
        assertThat(rows.get(0)).doesNotContainKey("phone");
    }

    private UserFollow follow(Long followerId, Long followeeId, String status) {
        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        follow.setStatus(status);
        return follow;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        return user;
    }
}
