package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PlaceRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.event.GrowthActionCompletedEvent;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.PlaceReview;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.place.PlaceReviewCreateRequest;
import com.skatehub.pojo.post.PostCreateRequest;
import com.skatehub.util.ActivitySignStatus;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthActionEventPublishingTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(7L, "skater", "USER");

    @Test
    void newDailyCheckinPublishesAfterGrowthGrant() {
        DailyCheckinRepository checkins = mock(DailyCheckinRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserGrowthService growth = mock(UserGrowthService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        User user = new User();
        user.setUserId(7L);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(checkins.findByUserIdAndCheckinDate(any(), any())).thenReturn(Optional.empty());
        when(checkins.findTopByUserIdOrderByCheckinDateDesc(7L)).thenReturn(Optional.empty());
        CheckinService service = new CheckinService(checkins, users, growth,
                Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneOffset.UTC), publisher);

        service.checkin(7L);

        var order = inOrder(checkins, growth, publisher);
        order.verify(checkins).save(any());
        order.verify(growth).grantExp(7L, "DAILY_CHECKIN", "DATE", 20260618L, 5);
        order.verify(publisher).publishEvent(new GrowthActionCompletedEvent(7L, "DAILY_CHECKIN"));
    }

    @Test
    void duplicateDailyCheckinDoesNotPublish() {
        DailyCheckinRepository checkins = mock(DailyCheckinRepository.class);
        UserRepository users = mock(UserRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        User user = new User();
        user.setUserId(7L);
        var existing = new com.skatehub.pojo.DailyCheckin();
        existing.setCheckinDate(java.time.LocalDate.of(2026, 6, 18));
        existing.setStreakDays(1);
        existing.setBaseExp(5);
        existing.setBonusExp(0);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(checkins.findByUserIdAndCheckinDate(7L, existing.getCheckinDate()))
                .thenReturn(Optional.of(existing));
        CheckinService service = new CheckinService(checkins, users, mock(UserGrowthService.class),
                Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneOffset.UTC), publisher);

        service.checkin(7L);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void postCreatePublishesAfterSaveAndGrowthGrant() {
        PostRepository posts = mock(PostRepository.class);
        UserGrowthService growth = mock(UserGrowthService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Post saved = new Post();
        saved.setPostId(11L);
        when(posts.save(any(Post.class))).thenReturn(saved);
        PostService service = new PostService(posts, mock(CommentRepository.class), mock(UserRepository.class),
                mock(InteractionRepository.class), mock(MessageNotifyService.class), growth, publisher);

        service.create(CURRENT_USER, new PostCreateRequest());

        var order = inOrder(posts, growth, publisher);
        order.verify(posts).save(any(Post.class));
        order.verify(growth).grantExp(7L, "POST_CREATE", "POST", 11L, 20);
        order.verify(publisher).publishEvent(new GrowthActionCompletedEvent(7L, "POST_CREATE"));
    }

    @Test
    void invalidPostUserDoesNotPublish() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PostService service = new PostService(mock(PostRepository.class), mock(CommentRepository.class),
                mock(UserRepository.class), mock(InteractionRepository.class), mock(MessageNotifyService.class),
                mock(UserGrowthService.class), publisher);

        assertThrows(BizException.class, () -> service.create(null, new PostCreateRequest()));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void activityCheckinPublishesAfterSaveAndGrowthGrant() {
        ActivitySignRepository signs = mock(ActivitySignRepository.class);
        UserGrowthService growth = mock(UserGrowthService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ActivitySign sign = new ActivitySign();
        sign.setSignStatus(ActivitySignStatus.APPROVED);
        sign.setIsCheckin("0");
        when(signs.findByActivityIdAndUserId(13L, 7L)).thenReturn(Optional.of(sign));
        ActivityService service = activityService(signs, growth, publisher);

        service.checkin(CURRENT_USER, 13L);

        var order = inOrder(signs, growth, publisher);
        order.verify(signs).save(sign);
        order.verify(growth).grantExp(7L, "ACTIVITY_CHECKIN", "ACTIVITY", 13L, 15);
        order.verify(publisher).publishEvent(new GrowthActionCompletedEvent(7L, "ACTIVITY_CHECKIN"));
    }

    @Test
    void activityRegistrationDoesNotPublish() {
        ActivityRepository activities = mock(ActivityRepository.class);
        ActivitySignRepository signs = mock(ActivitySignRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Activity activity = new Activity();
        activity.setActivityId(13L);
        activity.setUserId(99L);
        activity.setMaxNum(10);
        activity.setSignNum(0);
        activity.setActivityTime(java.time.LocalDateTime.now().plusDays(1));
        activity.setReviewStatus(com.skatehub.util.ActivityReviewStatus.APPROVED);
        activity.setActivityStatus(com.skatehub.util.ActivityStatus.SIGNUP_OPEN);
        activity.setStatus(com.skatehub.util.ActivityStatus.SIGNUP_OPEN);
        when(activities.findById(13L)).thenReturn(Optional.of(activity));
        when(signs.findByActivityIdAndUserId(13L, 7L)).thenReturn(Optional.empty());
        ActivityService service = new ActivityService(activities, signs, mock(UserRepository.class),
                mock(MessageNotifyService.class), mock(UserGrowthService.class), publisher);

        service.sign(CURRENT_USER, 13L);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void placeReviewPublishesAfterReviewAndAggregateWrites() {
        PlaceRepository places = mock(PlaceRepository.class);
        PlaceReviewRepository reviews = mock(PlaceReviewRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Place place = new Place();
        place.setPlaceId(17L);
        PlaceReview saved = new PlaceReview();
        saved.setReviewId(19L);
        saved.setPlaceId(17L);
        saved.setUserId(7L);
        saved.setScore(5);
        when(places.findById(17L)).thenReturn(Optional.of(place));
        when(reviews.save(any(PlaceReview.class))).thenReturn(saved);
        when(reviews.findByPlaceIdOrderByCreateTimeDesc(17L)).thenReturn(List.of(saved));
        PlaceReviewService service = new PlaceReviewService(places, reviews, mock(UserRepository.class), publisher);
        PlaceReviewCreateRequest request = new PlaceReviewCreateRequest();
        request.setPlaceId(17L);
        request.setScore(5);

        service.create(CURRENT_USER, request);

        var order = inOrder(reviews, places, publisher);
        order.verify(reviews).save(any(PlaceReview.class));
        order.verify(places).save(place);
        order.verify(publisher).publishEvent(new GrowthActionCompletedEvent(7L, "PLACE_REVIEW_CREATE"));
    }

    @Test
    void missingPlaceDoesNotPublish() {
        PlaceRepository places = mock(PlaceRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(places.findById(17L)).thenReturn(Optional.empty());
        PlaceReviewService service = new PlaceReviewService(places, mock(PlaceReviewRepository.class),
                mock(UserRepository.class), publisher);
        PlaceReviewCreateRequest request = new PlaceReviewCreateRequest();
        request.setPlaceId(17L);
        request.setScore(5);

        assertThrows(BizException.class, () -> service.create(CURRENT_USER, request));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void publishingMethodsHaveTransactionalBoundary() throws Exception {
        assertTransactional(PostService.class, "create", CurrentUser.class, PostCreateRequest.class);
        assertTransactional(ActivityService.class, "checkin", CurrentUser.class, Long.class);
        assertTransactional(PlaceReviewService.class, "create", CurrentUser.class, PlaceReviewCreateRequest.class);
    }

    private ActivityService activityService(ActivitySignRepository signs, UserGrowthService growth,
                                            ApplicationEventPublisher publisher) {
        return new ActivityService(mock(ActivityRepository.class), signs, mock(UserRepository.class),
                mock(MessageNotifyService.class), growth, publisher);
    }

    private void assertTransactional(Class<?> type, String name, Class<?>... parameters) throws Exception {
        assertEquals(Transactional.class, type.getMethod(name, parameters).getAnnotation(Transactional.class).annotationType());
    }
}
