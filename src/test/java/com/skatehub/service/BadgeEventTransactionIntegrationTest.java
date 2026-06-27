package com.skatehub.service;

import com.skatehub.dao.ActivitySignRepository;
import com.skatehub.dao.BadgeRepository;
import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.EventSignupRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.TrickCheckinRepository;
import com.skatehub.dao.UserBadgeRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.event.GrowthActionCompletedEvent;
import com.skatehub.pojo.ActivitySign;
import com.skatehub.pojo.Badge;
import com.skatehub.pojo.DailyCheckin;
import com.skatehub.pojo.PlaceReview;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserBadge;
import com.skatehub.pojo.skate.EventSignup;
import com.skatehub.pojo.skate.TrickCheckin;
import com.skatehub.pojo.skate.UserTrainingPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = BadgeEventTransactionIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:badge-events;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class BadgeEventTransactionIntegrationTest {

    private static final Instant TEST_NOW = Instant.parse("2026-06-18T08:00:00Z");

    @Autowired
    private TransactionalPublisher transactionalPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Autowired
    private DailyCheckinRepository dailyCheckinRepository;

    @Autowired
    private UserBadgeRepository userBadgeRepository;

    @SpyBean
    private BadgeService badgeService;

    private Long userId;

    @BeforeEach
    void seedEligibleUser() {
        userBadgeRepository.deleteAll();
        dailyCheckinRepository.deleteAll();
        badgeRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("transaction-user");
        user.setPassword("password");
        user.setBio("before");
        userId = userRepository.saveAndFlush(user).getUserId();

        Badge badge = new Badge();
        badge.setCode("FIRST_CHECKIN");
        badge.setName("First check-in");
        badge.setDescription("Complete a daily check-in");
        badge.setRarity("COMMON");
        badge.setIconCode("checkin");
        badge.setSortOrder(1);
        badge.setEnabled("1");
        badgeRepository.saveAndFlush(badge);

        DailyCheckin checkin = new DailyCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(LocalDate.of(2026, 6, 18));
        checkin.setStreakDays(1);
        checkin.setBaseExp(10);
        checkin.setBonusExp(0);
        checkin.setCreateTime(LocalDateTime.ofInstant(TEST_NOW, ZoneOffset.UTC));
        dailyCheckinRepository.saveAndFlush(checkin);
    }

    @AfterEach
    void restoreRealBadgeService() {
        reset(badgeService);
    }

    @Test
    void committedEventAwardsBadgeInNewTransaction() {
        transactionalPublisher.changeBioAndPublish(userId, "committed", false);

        assertEquals("committed", userRepository.findById(userId).orElseThrow().getBio());
        assertEquals(1, userBadgeRepository.count());
    }

    @Test
    void rolledBackEventNeitherAwardsBadgeNorKeepsPrimaryChange() {
        assertThrows(IllegalStateException.class,
                () -> transactionalPublisher.changeBioAndPublish(userId, "rolled back", true));

        assertEquals("before", userRepository.findById(userId).orElseThrow().getBio());
        assertEquals(0, userBadgeRepository.count());
    }

    @Test
    void repeatedCommittedEventsCreateOnlyOneAward() {
        transactionalPublisher.changeBioAndPublish(userId, "first", false);
        transactionalPublisher.changeBioAndPublish(userId, "second", false);

        assertEquals(1, userBadgeRepository.count());
    }

    @Test
    void listenerFailureDoesNotUndoCommittedPrimaryChange() {
        doThrow(new RuntimeException("badge storage unavailable"))
                .when(badgeService).evaluateAndList(anyLong());

        transactionalPublisher.changeBioAndPublish(userId, "still committed", false);

        assertEquals("still committed", userRepository.findById(userId).orElseThrow().getBio());
        assertEquals(0, userBadgeRepository.count());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = {
            User.class,
            Badge.class,
            UserBadge.class,
            DailyCheckin.class,
            Post.class,
            ActivitySign.class,
            PlaceReview.class,
            TrickCheckin.class,
            UserTrainingPlan.class,
            EventSignup.class
    })
    @EnableJpaRepositories(
            basePackageClasses = UserRepository.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            UserRepository.class,
                            BadgeRepository.class,
                            UserBadgeRepository.class,
                            DailyCheckinRepository.class,
                            PostRepository.class,
                            ActivitySignRepository.class,
                            PlaceReviewRepository.class,
                            TrickCheckinRepository.class,
                            UserTrainingPlanRepository.class,
                            EventSignupRepository.class
                    }),
            considerNestedRepositories = true
    )
    static class TestApplication {

        @Bean
        Clock clock() {
            return Clock.fixed(TEST_NOW, ZoneOffset.UTC);
        }

        @Bean
        BadgeService badgeService(
                BadgeRepository badgeRepository,
                UserBadgeRepository userBadgeRepository,
                DailyCheckinRepository dailyCheckinRepository,
                PostRepository postRepository,
                ActivitySignRepository activitySignRepository,
                PlaceReviewRepository placeReviewRepository,
                TrickCheckinRepository trickCheckinRepository,
                UserTrainingPlanRepository userTrainingPlanRepository,
                EventSignupRepository eventSignupRepository,
                UserRepository userRepository,
                Clock clock) {
            return new BadgeService(
                    badgeRepository,
                    userBadgeRepository,
                    dailyCheckinRepository,
                    postRepository,
                    activitySignRepository,
                    placeReviewRepository,
                    trickCheckinRepository,
                    userTrainingPlanRepository,
                    eventSignupRepository,
                    userRepository,
                    clock);
        }

        @Bean
        BadgeAwardListener badgeAwardListener(BadgeService badgeService) {
            return new BadgeAwardListener(badgeService);
        }

        @Bean
        TransactionalPublisher transactionalPublisher(
                UserRepository userRepository,
                ApplicationEventPublisher eventPublisher) {
            return new TransactionalPublisher(userRepository, eventPublisher);
        }
    }

    static class TransactionalPublisher {
        private final UserRepository userRepository;
        private final ApplicationEventPublisher eventPublisher;

        TransactionalPublisher(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
            this.userRepository = userRepository;
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void changeBioAndPublish(Long targetUserId, String bio, boolean rollBack) {
            User user = userRepository.findById(targetUserId).orElseThrow();
            user.setBio(bio);
            eventPublisher.publishEvent(new GrowthActionCompletedEvent(targetUserId, "DAILY_CHECKIN"));
            if (rollBack) {
                throw new IllegalStateException("roll back primary transaction");
            }
        }
    }
}
