package com.skatehub.security;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.AiCoachMessageRepository;
import com.skatehub.dao.AiCoachSessionRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.MessageRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.AiCoachSession;
import com.skatehub.pojo.Message;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.Video;
import com.skatehub.service.ActivityService;
import com.skatehub.service.MessageNotifyService;
import com.skatehub.service.PostService;
import com.skatehub.service.UserGrowthService;
import com.skatehub.service.UserService;
import com.skatehub.service.VideoService;
import com.skatehub.service.ai.AiCoachAssistantService;
import com.skatehub.service.ai.AiCoachContentRecommendService;
import com.skatehub.service.ai.AiGatewayService;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataOwnershipServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private InteractionRepository interactionRepository;
    @Mock private MessageNotifyService messageNotifyService;
    @Mock private UserGrowthService userGrowthService;
    @Mock private VideoRepository videoRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private AiGatewayService aiGatewayService;
    @Mock private AiCoachContentRecommendService aiCoachContentRecommendService;
    @Mock private AiCoachSessionRepository aiCoachSessionRepository;
    @Mock private AiCoachMessageRepository aiCoachMessageRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicy passwordPolicy;

    private PostService postService;
    private UserService userService;
    private VideoService videoService;
    private ActivityService activityService;
    private AiCoachAssistantService aiCoachAssistantService;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                commentRepository,
                userRepository,
                interactionRepository,
                messageNotifyService,
                userGrowthService
        );
        userService = new UserService(
                userRepository,
                messageRepository,
                postRepository,
                activityRepository,
                interactionRepository,
                commentRepository,
                passwordEncoder,
                userGrowthService,
                passwordPolicy
        );
        videoService = new VideoService(videoRepository);
        activityService = new ActivityService(
                activityRepository,
                activitySignRepository,
                userRepository,
                messageNotifyService,
                userGrowthService
        );
        aiCoachAssistantService = new AiCoachAssistantService(
                aiGatewayService,
                aiCoachContentRecommendService,
                aiCoachSessionRepository,
                aiCoachMessageRepository
        );
    }

    @Test
    void nonAdminCannotDeleteOtherPostByPassingAdminMode() {
        assertThatThrownBy(() -> postService.delete(user(1L), 9L, true))
                .isInstanceOf(BizException.class);

        verify(postRepository, never()).findById(9L);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void userCannotDeleteAnotherUsersPost() {
        Post post = new Post();
        post.setPostId(9L);
        post.setUserId(2L);
        when(postRepository.findById(9L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(user(1L), 9L, false))
                .isInstanceOf(BizException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void userCannotMarkAnotherUsersMessageRead() {
        Message message = new Message();
        message.setMsgId(5L);
        message.setUserId(2L);
        message.setIsRead("0");
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> userService.readMessage(user(1L), 5L))
                .isInstanceOf(BizException.class);

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void nonAdminCannotDeleteOtherVideoByPassingAdminMode() {
        assertThatThrownBy(() -> videoService.delete(user(1L), 8L, true))
                .isInstanceOf(BizException.class);

        verify(videoRepository, never()).findById(8L);
        verify(videoRepository, never()).delete(any(Video.class));
    }

    @Test
    void adminCannotUseUserActivityDeletePathForGlobalOperation() {
        Activity activity = new Activity();
        activity.setActivityId(7L);
        activity.setUserId(2L);
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.delete(admin(1L), 7L, false))
                .isInstanceOf(BizException.class);

        verify(activityRepository, never()).delete(any(Activity.class));
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void adminCannotCloseSignupThroughUserActivityPath() {
        Activity activity = new Activity();
        activity.setActivityId(7L);
        activity.setUserId(2L);
        activity.setReviewStatus(ActivityReviewStatus.APPROVED);
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.closeSignup(admin(1L), 7L))
                .isInstanceOf(BizException.class);

        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void aiSessionMessagesMustResolveByCurrentUser() {
        when(aiCoachSessionRepository.findBySessionIdAndUserId("coach-1", 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiCoachAssistantService.getSessionMessages(user(1L), "coach-1"))
                .isInstanceOf(BizException.class);

        verify(aiCoachMessageRepository, never()).findBySessionIdOrderByCreateTimeAscMsgIdAsc(anyString());
    }

    private CurrentUser user(Long userId) {
        return new CurrentUser(userId, "USER", "user-" + userId);
    }

    private CurrentUser admin(Long userId) {
        return new CurrentUser(userId, "ADMIN", "admin-" + userId);
    }
}
