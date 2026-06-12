package com.skatehub.service;

import com.skatehub.dao.*;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.admin.AdminCommentContextResponse;
import com.skatehub.pojo.admin.AdminCommentResponse;
import com.skatehub.util.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceCommentTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private NoticeRepository noticeRepository;
    @Mock private NewsRepository newsRepository;
    @Mock private PlaceRepository placeRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private CommunityBulletinService communityBulletinService;
    @Mock private CommunityBulletinRepository communityBulletinRepository;
    @Mock private ReportRecordRepository reportRecordRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void listCommentsReturnsPostUserAndParentDetails() {
        Comment parent = comment(10L, 9L, 1L, 0L, "前排支持", "2026-06-04T09:00:00");
        Comment reply = comment(11L, 9L, 2L, 10L, "落地姿势很稳", "2026-06-04T10:00:00");
        Post post = post(9L, "ollie 练习记录");
        User user = user(2L, "skater-a", "/avatar-a.png");

        when(commentRepository.searchAdminComments(eq("%落地%"), eq(9L), eq(2L), eq("reply"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reply), PageRequest.of(0, 10), 1));
        when(postRepository.findAllById(List.of(9L))).thenReturn(List.of(post));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user));
        when(commentRepository.findAllById(List.of(10L))).thenReturn(List.of(parent));

        PageResult<AdminCommentResponse> result = adminService.listComments(1, 10, "落地", 9L, 2L, "reply", "latest");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        AdminCommentResponse row = result.getList().get(0);
        assertThat(row.getCommentId()).isEqualTo(11L);
        assertThat(row.getPostTitle()).isEqualTo("ollie 练习记录");
        assertThat(row.getUsername()).isEqualTo("skater-a");
        assertThat(row.getAvatar()).isEqualTo("/avatar-a.png");
        assertThat(row.getParentContent()).isEqualTo("前排支持");
        assertThat(row.getReplyType()).isEqualTo("reply");
        assertThat(row.getContentLength()).isEqualTo(6);
    }

    @Test
    void commentContextReturnsCurrentParentAndNearbyComments() {
        Comment parent = comment(10L, 9L, 1L, 0L, "前排支持", "2026-06-04T09:00:00");
        Comment current = comment(11L, 9L, 2L, 10L, "落地姿势很稳", "2026-06-04T10:00:00");
        Comment nearby = comment(12L, 9L, 3L, 0L, "求教程", "2026-06-04T10:05:00");
        Post post = post(9L, "ollie 练习记录");
        post.setContent("今天练了十组 ollie，记录一下脚位和肩线。");
        User user = user(2L, "skater-a", "/avatar-a.png");

        when(commentRepository.findById(11L)).thenReturn(Optional.of(current));
        when(postRepository.findById(9L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.findTop20ByPostIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(parent, current, nearby));
        when(userRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(user(1L, "owner", ""), user, user(3L, "newbie", "")));
        when(postRepository.findAllById(List.of(9L))).thenReturn(List.of(post));
        when(commentRepository.findAllById(List.of(10L))).thenReturn(List.of(parent));

        AdminCommentContextResponse context = adminService.commentContext(11L);

        assertThat(context.getPostId()).isEqualTo(9L);
        assertThat(context.getPostTitle()).isEqualTo("ollie 练习记录");
        assertThat(context.getPostContentPreview()).contains("今天练了十组");
        assertThat(context.getCurrentComment().getCommentId()).isEqualTo(11L);
        assertThat(context.getParentComment().getCommentId()).isEqualTo(10L);
        assertThat(context.getNearbyComments()).extracting(AdminCommentResponse::getCommentId)
                .containsExactly(10L, 11L, 12L);
    }

    @Test
    void batchDeleteCommentsReturnsDeletedCount() {
        long deleted = adminService.deleteComments(Arrays.asList(11L, 12L, 11L));

        assertThat(deleted).isEqualTo(2);
        verify(commentRepository).deleteAllByIdInBatch(List.of(11L, 12L));
    }

    @Test
    void batchDeleteCommentsRejectsInvalidIds() {
        assertThatThrownBy(() -> adminService.deleteComments(Arrays.asList(11L, null)))
                .hasMessageContaining("ID");
        assertThatThrownBy(() -> adminService.deleteComments(Arrays.asList(11L, -1L)))
                .hasMessageContaining("ID");
    }

    private Comment comment(Long id, Long postId, Long userId, Long parentId, String content, String time) {
        Comment comment = new Comment();
        comment.setCommentId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content);
        comment.setCreateTime(LocalDateTime.parse(time));
        return comment;
    }

    private Post post(Long id, String title) {
        Post post = new Post();
        post.setPostId(id);
        post.setTitle(title);
        post.setContent("");
        return post;
    }

    private User user(Long id, String username, String avatar) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setAvatar(avatar);
        return user;
    }
}
