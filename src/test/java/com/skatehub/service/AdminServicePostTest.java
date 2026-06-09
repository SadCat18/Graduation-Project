package com.skatehub.service;

import com.skatehub.dao.*;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.admin.AdminPostDetailResponse;
import com.skatehub.pojo.admin.AdminPostResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServicePostTest {

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
    void listPostsReturnsAdminPostDetailsAndFilters() {
        Post post = post(9L, 2L, "Ollie 训练节奏", "今天练了十组 Ollie，重点是脚位、肩线和落地稳定。", "a.jpg,b.jpg", "技巧教学", "1");
        User user = user(2L, "skater-a", "/avatar-a.png");

        when(postRepository.searchAdminPosts(eq("ollie"), eq("技巧教学"), eq("top"), eq("comments"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user));
        when(commentRepository.countByPostId(9L)).thenReturn(8L);
        when(reportRecordRepository.countByTargetTypeAndTargetId("POST", 9L)).thenReturn(2L);

        PageResult<AdminPostResponse> result = adminService.listPosts(1, 10, "ollie", "技巧教学", "top", "comments");

        assertThat(result.getTotal()).isEqualTo(1);
        AdminPostResponse row = result.getList().get(0);
        assertThat(row.getPostId()).isEqualTo(9L);
        assertThat(row.getUsername()).isEqualTo("skater-a");
        assertThat(row.getAvatar()).isEqualTo("/avatar-a.png");
        assertThat(row.getContentPreview()).contains("今天练了十组 Ollie");
        assertThat(row.getImageCount()).isEqualTo(2);
        assertThat(row.getCommentCount()).isEqualTo(8);
        assertThat(row.getReportCount()).isEqualTo(2);
    }

    @Test
    void postDetailReturnsFullPostAndRecentComments() {
        Post post = post(9L, 2L, "Ollie 训练节奏", "完整正文内容", "a.jpg,b.jpg", "技巧教学", "0");
        User author = user(2L, "skater-a", "/avatar-a.png");
        Comment first = comment(100L, 9L, 3L, "求脚位图", "2026-06-04T10:00:00");
        Comment second = comment(101L, 9L, 4L, "落地讲得很清楚", "2026-06-04T10:10:00");

        when(postRepository.findById(9L)).thenReturn(Optional.of(post));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(author));
        when(commentRepository.countByPostId(9L)).thenReturn(2L);
        when(reportRecordRepository.countByTargetTypeAndTargetId("POST", 9L)).thenReturn(0L);
        when(commentRepository.findTop5ByPostIdOrderByCreateTimeDesc(9L)).thenReturn(List.of(second, first));
        when(userRepository.findAllById(List.of(4L, 3L))).thenReturn(List.of(user(4L, "newbie", ""), user(3L, "viewer", "")));

        AdminPostDetailResponse detail = adminService.postDetail(9L);

        assertThat(detail.getPost().getTitle()).isEqualTo("Ollie 训练节奏");
        assertThat(detail.getImageList()).containsExactly("a.jpg", "b.jpg");
        assertThat(detail.getRecentComments()).extracting(AdminPostDetailResponse.RecentComment::getCommentId)
                .containsExactly(101L, 100L);
    }

    @Test
    void batchPostOperationsDeduplicateIdsAndReturnCounts() {
        Post first = post(9L, 2L, "A", "正文", "", "技巧教学", "0");
        Post second = post(10L, 2L, "B", "正文", "", "技巧教学", "0");
        when(postRepository.findAllById(List.of(9L, 10L))).thenReturn(List.of(first, second));

        long topped = adminService.topPosts(Arrays.asList(9L, 10L, null, 9L), "1");
        long deleted = adminService.deletePosts(Arrays.asList(9L, 10L, null, 9L));

        assertThat(topped).isEqualTo(2);
        assertThat(first.getIsTop()).isEqualTo("1");
        assertThat(second.getIsTop()).isEqualTo("1");
        verify(postRepository).saveAll(List.of(first, second));
        assertThat(deleted).isEqualTo(2);
        verify(postRepository).deleteAllByIdInBatch(List.of(9L, 10L));
    }

    private Post post(Long postId, Long userId, String title, String content, String images, String category, String isTop) {
        Post post = new Post();
        post.setPostId(postId);
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        post.setImages(images);
        post.setCategory(category);
        post.setIsTop(isTop);
        post.setLikeCount(12);
        post.setCollectCount(3);
        post.setCreateTime(LocalDateTime.parse("2026-06-04T09:00:00"));
        return post;
    }

    private User user(Long userId, String username, String avatar) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setAvatar(avatar);
        return user;
    }

    private Comment comment(Long commentId, Long postId, Long userId, String content, String createTime) {
        Comment comment = new Comment();
        comment.setCommentId(commentId);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(0L);
        comment.setContent(content);
        comment.setCreateTime(LocalDateTime.parse(createTime));
        return comment;
    }
}
