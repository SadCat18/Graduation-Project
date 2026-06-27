package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.event.GrowthActionCompletedEvent;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.comment.CommentCreateRequest;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceReplyTest {

    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private InteractionRepository interactionRepository;
    @Mock private MessageNotifyService messageNotifyService;
    @Mock private UserGrowthService userGrowthService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PostService postService;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, commentRepository, userRepository, interactionRepository,
                messageNotifyService, userGrowthService, eventPublisher);
        currentUser = new CurrentUser(2L, "USER", "reply-user");
    }

    @Test
    void commentsReturnsRootCommentsWithNestedReplies() {
        Comment root = comment(10L, 7L, 1L, 0L, null, "动作很稳");
        Comment reply = comment(11L, 7L, 2L, 10L, 1L, "回复 @owner：肩线很关键");
        User owner = user(1L, "owner");
        User replier = user(2L, "replier");
        when(postRepository.findById(7L)).thenReturn(Optional.of(post(7L, 1L)));
        when(commentRepository.findByPostIdAndParentIdOrderByCreateTimeAsc(7L, 0L)).thenReturn(List.of(root));
        when(commentRepository.findByPostIdAndParentIdNotOrderByCreateTimeAsc(7L, 0L)).thenReturn(List.of(reply));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(replier));

        List<Map<String, Object>> result = postService.comments(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("commentId", 10L);
        List<?> replies = (List<?>) result.get(0).get("replies");
        assertThat(replies).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> replyRow = (Map<String, Object>) replies.get(0);
        assertThat(replyRow).containsEntry("commentId", 11L)
                .containsEntry("replyToUserId", 1L)
                .containsEntry("replyToUsername", "owner");
    }

    @Test
    void addReplyStoresParentAndReplyTargetThenNotifiesParentAuthor() {
        Post post = post(7L, 1L);
        Comment parent = comment(10L, 7L, 1L, 0L, null, "动作很稳");
        CommentCreateRequest request = new CommentCreateRequest();
        request.setParentId(10L);
        request.setReplyToUserId(1L);
        request.setContent("我也这么练");
        when(postRepository.findById(7L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            saved.setCommentId(12L);
            return saved;
        });

        Comment saved = postService.addComment(currentUser, 7L, request);

        assertThat(saved.getParentId()).isEqualTo(10L);
        assertThat(saved.getReplyToUserId()).isEqualTo(1L);
        verify(userGrowthService).grantExp(2L, "COMMENT_REPLY", "COMMENT", 12L, 2);
        verify(messageNotifyService).send(1L, "COMMENT", "你的评论收到回复：我也这么练", "POST", 7L);
    }

    @Test
    void rootCommentUsesRootGrowthActionAndPostAuthorNotification() {
        Post post = post(7L, 1L);
        CommentCreateRequest request = new CommentCreateRequest();
        request.setContent("前排支持");
        request.setParentId(0L);
        when(postRepository.findById(7L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            saved.setCommentId(13L);
            return saved;
        });

        postService.addComment(currentUser, 7L, request);

        verify(userGrowthService).grantExp(2L, "COMMENT_CREATE", "COMMENT", 13L, 3);
        verify(messageNotifyService).send(1L, "COMMENT", "你的帖子收到新评论：ollie 练习", "POST", 7L);
    }

    @Test
    void commentLikeTogglesCommentInteractionAndUpdatesLikeCount() {
        Comment comment = comment(10L, 7L, 1L, 0L, null, "动作很稳");
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(interactionRepository.findByUserIdAndTargetTypeAndTargetIdAndType(2L, "COMMENT", 10L, "LIKE"))
                .thenReturn(Optional.empty());
        when(interactionRepository.countByTargetTypeAndTargetIdAndType("COMMENT", 10L, "LIKE")).thenReturn(1L);

        Map<String, Object> result = postService.toggleCommentLike(currentUser, 10L);

        assertThat(result).containsEntry("active", true).containsEntry("likeCount", 1L);
        assertThat(comment.getLikeCount()).isEqualTo(1);
        verify(commentRepository).save(comment);
    }

    private Comment comment(Long id, Long postId, Long userId, Long parentId, Long replyToUserId, String content) {
        Comment comment = new Comment();
        comment.setCommentId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setCreateTime(LocalDateTime.parse("2026-06-27T10:00:00"));
        return comment;
    }

    private Post post(Long id, Long userId) {
        Post post = new Post();
        post.setPostId(id);
        post.setUserId(userId);
        post.setTitle("ollie 练习");
        return post;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        return user;
    }
}
