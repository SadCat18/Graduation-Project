package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.dao.UserTrainingRecordRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Interaction;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivityStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceQueryOptimizationTest {

    @Test
    void usesBoundedCandidatesAndBatchLoadsUserSignals() {
        UserRepository userRepository = mock(UserRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        ActivityRepository activityRepository = mock(ActivityRepository.class);
        InteractionRepository interactionRepository = mock(InteractionRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        ActivitySignRepository activitySignRepository = mock(ActivitySignRepository.class);
        PostService postService = mock(PostService.class);
        ActivityService activityService = mock(ActivityService.class);
        CommunityBulletinService bulletinService = mock(CommunityBulletinService.class);
        TrainingPlanRepository trainingPlanRepository = mock(TrainingPlanRepository.class);
        UserTrainingPlanRepository userTrainingPlanRepository = mock(UserTrainingPlanRepository.class);
        UserTrainingRecordRepository userTrainingRecordRepository = mock(UserTrainingRecordRepository.class);

        RecommendationService service = new RecommendationService(
                userRepository,
                postRepository,
                activityRepository,
                mock(CommunityBulletinRepository.class),
                interactionRepository,
                commentRepository,
                activitySignRepository,
                postService,
                activityService,
                bulletinService,
                trainingPlanRepository,
                userTrainingPlanRepository,
                userTrainingRecordRepository
        );

        User currentUser = user(7L, "当前用户");
        currentUser.setSkateStyle("street");
        User author = user(8L, "帖子作者");
        User publisher = user(9L, "活动发起人");
        when(userRepository.findById(7L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findAllById(any())).thenReturn(List.of(author, publisher));

        Interaction likedPost = new Interaction();
        likedPost.setTargetId(101L);
        Comment userComment = new Comment();
        userComment.setPostId(102L);
        when(interactionRepository.findTop50ByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(7L, "POST", "LIKE"))
                .thenReturn(List.of(likedPost));
        when(interactionRepository.findTop50ByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(7L, "POST", "COLLECT"))
                .thenReturn(List.of());
        when(commentRepository.findTop50ByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of(userComment));
        when(postRepository.findAllById(any())).thenReturn(List.of(post(101L, 8L, "技巧交流")));

        ActivitySign historicalSign = new ActivitySign();
        historicalSign.setActivityId(201L);
        when(activitySignRepository.findTop50ByUserIdOrderBySignTimeDesc(7L)).thenReturn(List.of(historicalSign));
        when(activityRepository.findAllById(any())).thenReturn(List.of(activity(201L, 9L, "杭州")));
        when(activityRepository.findTop20ByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of());

        Post candidatePost = post(301L, 8L, "技巧交流");
        candidatePost.setTitle("Street Ollie 教程");
        candidatePost.setLikeCount(20);
        when(postRepository.findTop100ByOrderByIsTopDescLikeCountDescCollectCountDescCreateTimeDesc())
                .thenReturn(List.of(candidatePost));
        when(commentRepository.countByPostIds(List.of(301L)))
                .thenReturn(Collections.singletonList(new Object[]{301L, 4L}));

        Activity candidateActivity = activity(401L, 9L, "杭州");
        candidateActivity.setTitle("周末街式刷街");
        candidateActivity.setReviewStatus(ActivityReviewStatus.APPROVED);
        candidateActivity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
        when(activityRepository.findTop80ByReviewStatusAndActivityStatusInOrderByCreateTimeDesc(
                ActivityReviewStatus.APPROVED,
                List.of(ActivityStatus.SIGNUP_OPEN, ActivityStatus.FULL, ActivityStatus.CANCELED, ActivityStatus.ENDED)
        )).thenReturn(List.of(candidateActivity));
        when(activitySignRepository.findByUserIdAndActivityIdIn(7L, List.of(401L))).thenReturn(List.of());

        when(bulletinService.listPublicApprovedAll()).thenReturn(List.of());
        when(bulletinService.listPublicApproved(5)).thenReturn(List.of());
        when(trainingPlanRepository.findByStatusOrderByPlanIdAsc("1")).thenReturn(List.of());
        when(userTrainingPlanRepository.findByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of());
        when(userTrainingRecordRepository.findByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of());

        Map<String, Object> result = service.recommendForUser(7L);

        List<Map<String, Object>> posts = (List<Map<String, Object>>) result.get("posts");
        List<Map<String, Object>> activities = (List<Map<String, Object>>) result.get("activities");
        assertThat(posts).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("postId", 301L);
            assertThat(item).containsEntry("authorName", "帖子作者");
            assertThat(item).containsEntry("commentCount", 4L);
        });
        assertThat(activities).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("activityId", 401L);
            assertThat(item).containsEntry("publisherName", "活动发起人");
        });

        verify(postRepository, never()).findAll();
        verify(activityRepository, never()).findAll();
        verify(commentRepository, never()).findAll();
        verify(postRepository, never()).findById(anyLong());
        verify(activityRepository, never()).findById(anyLong());
        verify(postService, never()).detail(anyLong());
        verify(activityService, never()).list(any(), any(), any(), any(), any(), any(), any());
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setLevel(2);
        return user;
    }

    private static Post post(Long id, Long userId, String category) {
        Post post = new Post();
        post.setPostId(id);
        post.setUserId(userId);
        post.setTitle("帖子 " + id);
        post.setContent("street practice");
        post.setCategory(category);
        post.setLikeCount(1);
        post.setCollectCount(0);
        post.setIsTop("0");
        post.setCreateTime(LocalDateTime.now());
        return post;
    }

    private static Activity activity(Long id, Long userId, String city) {
        Activity activity = new Activity();
        activity.setActivityId(id);
        activity.setUserId(userId);
        activity.setTitle("活动 " + id);
        activity.setContent("street session");
        activity.setCity(city);
        activity.setReviewStatus(ActivityReviewStatus.APPROVED);
        activity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setSignNum(2);
        activity.setMaxNum(10);
        activity.setCreateTime(LocalDateTime.now());
        return activity;
    }
}
