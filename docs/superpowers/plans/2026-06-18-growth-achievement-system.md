# SkateHub Growth and Achievement System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add daily check-in, total and weekly leaderboards, and eight collectible street-style achievement badges to the existing user profile.

**Architecture:** Extend the existing `UserGrowthService` ledger instead of adding a second points system. New focused services own check-in, leaderboard, and badge behavior; successful domain actions publish badge-evaluation events after their primary writes. A standalone Vue growth-center component is mounted as one new profile tab so the already-large profile page receives only a small integration change.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, MySQL 8, JUnit 5, Mockito, Vue 3 Composition API, Axios, local SVG/CSS, Node source-contract tests.

---

## File Map

**Backend persistence**

- Create `src/main/java/com/skatehub/pojo/DailyCheckin.java`: one persisted daily check-in.
- Create `src/main/java/com/skatehub/pojo/Badge.java`: seeded badge catalog row.
- Create `src/main/java/com/skatehub/pojo/UserBadge.java`: idempotent user-to-badge award.
- Create `src/main/java/com/skatehub/dao/DailyCheckinRepository.java`.
- Create `src/main/java/com/skatehub/dao/BadgeRepository.java`.
- Create `src/main/java/com/skatehub/dao/UserBadgeRepository.java`.
- Create `src/main/java/com/skatehub/dao/LeaderboardQueryRepository.java`: MySQL window-function queries isolated from services.
- Modify existing repositories only to add explicit count methods used by badge progress.
- Create `database/add-growth-achievement-system.sql` and modify `database/init.sql` with identical tables, indexes, and seed rows.

**Backend behavior and API**

- Create `src/main/java/com/skatehub/service/CheckinService.java`.
- Create `src/main/java/com/skatehub/service/LeaderboardService.java`.
- Create `src/main/java/com/skatehub/service/BadgeService.java`.
- Create `src/main/java/com/skatehub/service/BadgeAwardListener.java`.
- Create response records under `src/main/java/com/skatehub/pojo/growth/`.
- Modify `UserController.java`, `PostService.java`, `ActivityService.java`, and `PlaceReviewService.java` only at their successful action boundaries.

**Frontend**

- Create `frontend/src/components/growth/GrowthCenterPanel.vue`: data loading and layout.
- Create `frontend/src/components/growth/AchievementBadge.vue`: shield sticker SVG/CSS renderer.
- Create `frontend/src/components/growth/BadgeUnlockModal.vue`: accessible unlock presentation.
- Create `frontend/src/constants/badges.js`: local hand-drawn SVG paths keyed by backend `iconCode`.
- Modify `frontend/src/api/index.js` and `frontend/src/views/ProfilePage.vue` for API calls and tab mounting.

## Task 1: Persistence Model and Seed Catalog

**Files:**
- Create: `src/main/java/com/skatehub/pojo/DailyCheckin.java`
- Create: `src/main/java/com/skatehub/pojo/Badge.java`
- Create: `src/main/java/com/skatehub/pojo/UserBadge.java`
- Create: `src/main/java/com/skatehub/dao/DailyCheckinRepository.java`
- Create: `src/main/java/com/skatehub/dao/BadgeRepository.java`
- Create: `src/main/java/com/skatehub/dao/UserBadgeRepository.java`
- Create: `database/add-growth-achievement-system.sql`
- Modify: `database/init.sql`
- Test: `src/test/java/com/skatehub/growth/GrowthSchemaContractTest.java`

- [ ] **Step 1: Write the failing schema contract test**

```java
package com.skatehub.growth;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class GrowthSchemaContractTest {
    @Test
    void migrationDefinesUniqueAwardsAndAllEightBadges() throws Exception {
        String sql = Files.readString(Path.of("database/add-growth-achievement-system.sql"));
        assertThat(sql).contains("UNIQUE KEY uk_daily_checkin_user_date (user_id, checkin_date)");
        assertThat(sql).contains("UNIQUE KEY uk_user_badge_user_badge (user_id, badge_id)");
        assertThat(sql).contains(
                "FIRST_CHECKIN", "STREAK_7", "CHECKIN_30", "FIRST_POST",
                "POST_10", "FIRST_ACTIVITY_CHECKIN", "ACTIVITY_CHECKIN_3", "PLACE_REVIEW_3");
    }
}
```

- [ ] **Step 2: Run the test and confirm the missing migration failure**

Run: `./mvnw -Dtest=GrowthSchemaContractTest test`

Expected: FAIL because `database/add-growth-achievement-system.sql` does not exist.

- [ ] **Step 3: Add the three entities and repositories**

Use these table-level constraints in the entities:

```java
@Getter
@Setter
@Entity
@Table(name = "tb_daily_checkin", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_checkin_user_date", columnNames = {"user_id", "checkin_date"}))
public class DailyCheckin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checkinId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private LocalDate checkinDate;
    @Column(nullable = false) private Integer streakDays;
    @Column(nullable = false) private Integer baseExp;
    @Column(nullable = false) private Integer bonusExp;
    @Column(nullable = false) private LocalDateTime createTime;
}
```

```java
@Getter
@Setter
@Entity
@Table(name = "tb_badge")
public class Badge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long badgeId;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 30) private String name;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false, length = 16) private String rarity;
    @Column(nullable = false, length = 40) private String iconCode;
    @Column(nullable = false) private Integer sortOrder;
    @Column(nullable = false, length = 1) private String enabled;
}
```

```java
@Getter
@Setter
@Entity
@Table(name = "tb_user_badge", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_badge_user_badge", columnNames = {"user_id", "badge_id"}))
public class UserBadge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userBadgeId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long badgeId;
    @Column(nullable = false) private LocalDateTime unlockTime;
}
```

Repositories must expose exactly the queries needed by later services:

```java
public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {
    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate date);
    Optional<DailyCheckin> findTopByUserIdOrderByCheckinDateDesc(Long userId);
    long countByUserId(Long userId);
    List<DailyCheckin> findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(
            Long userId, LocalDate start, LocalDate end);
}
```

```java
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByEnabledOrderBySortOrderAsc(String enabled);
    Optional<Badge> findByCode(String code);
}

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserIdOrderByUnlockTimeDesc(Long userId);
    Optional<UserBadge> findByUserIdAndBadgeId(Long userId, Long badgeId);
}
```

- [ ] **Step 4: Add repeatable SQL and all eight seed rows**

Both SQL files must define the same three tables and indexes. Seed each row with an idempotent `INSERT INTO` plus `WHERE NOT EXISTS`, for example:

```sql
INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'STREAK_7', '坚持训练', '连续签到 7 天', 'RARE', 'streak-seven', 20, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'STREAK_7');
```

Use rarity values `COMMON`, `RARE`, `EPIC`, and `LEGENDARY`, and the assignments in the approved design document.

- [ ] **Step 5: Run the schema contract and compile tests**

Run: `./mvnw -Dtest=GrowthSchemaContractTest test`

Expected: PASS.

- [ ] **Step 6: Commit the persistence slice**

```bash
git add database/init.sql database/add-growth-achievement-system.sql src/main/java/com/skatehub/pojo/DailyCheckin.java src/main/java/com/skatehub/pojo/Badge.java src/main/java/com/skatehub/pojo/UserBadge.java src/main/java/com/skatehub/dao/DailyCheckinRepository.java src/main/java/com/skatehub/dao/BadgeRepository.java src/main/java/com/skatehub/dao/UserBadgeRepository.java src/test/java/com/skatehub/growth/GrowthSchemaContractTest.java
git commit -m "feat: add growth persistence model"
```

## Task 2: Idempotent Daily Check-in

**Files:**
- Create: `src/main/java/com/skatehub/config/GrowthTimeConfig.java`
- Create: `src/main/java/com/skatehub/pojo/growth/CheckinResponse.java`
- Create: `src/main/java/com/skatehub/pojo/growth/CheckinStatusResponse.java`
- Create: `src/main/java/com/skatehub/pojo/growth/CheckinCalendarResponse.java`
- Create: `src/main/java/com/skatehub/service/CheckinService.java`
- Modify: `src/main/java/com/skatehub/dao/UserRepository.java`
- Test: `src/test/java/com/skatehub/service/CheckinServiceTest.java`

- [ ] **Step 1: Write failing tests for first, repeated, seventh-day, and broken-streak check-ins**

```java
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {
    @Mock DailyCheckinRepository checkinRepository;
    @Mock UserRepository userRepository;
    @Mock UserGrowthService growthService;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void allowUserLockAndEchoSavedCheckin() {
        User user = new User();
        user.setUserId(7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        lenient().when(checkinRepository.save(any(DailyCheckin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstCheckinGrantsFiveExp() {
        when(checkinRepository.findByUserIdAndCheckinDate(7L, LocalDate.of(2026, 6, 18)))
                .thenReturn(Optional.empty());
        when(checkinRepository.findTopByUserIdOrderByCheckinDateDesc(7L)).thenReturn(Optional.empty());
        when(checkinRepository.countByUserId(7L)).thenReturn(1L);
        CheckinService service = service();

        CheckinResponse response = service.checkin(7L);

        assertThat(response.streakDays()).isEqualTo(1);
        assertThat(response.baseExp()).isEqualTo(5);
        assertThat(response.bonusExp()).isZero();
        verify(growthService).grantExp(7L, "DAILY_CHECKIN", "DATE", 20260618L, 5);
    }

    @Test
    void repeatedCheckinReturnsStoredResultWithoutGrantingAgain() {
        DailyCheckin stored = checkin(LocalDate.of(2026, 6, 18), 4, 5, 0);
        when(checkinRepository.findByUserIdAndCheckinDate(7L, stored.getCheckinDate()))
                .thenReturn(Optional.of(stored));

        CheckinResponse response = service().checkin(7L);

        assertThat(response.alreadyCheckedIn()).isTrue();
        verifyNoInteractions(growthService);
    }

    @Test
    void seventhConsecutiveDayGrantsTwentyBonusExp() {
        when(checkinRepository.findByUserIdAndCheckinDate(eq(7L), any())).thenReturn(Optional.empty());
        when(checkinRepository.findTopByUserIdOrderByCheckinDateDesc(7L))
                .thenReturn(Optional.of(checkin(LocalDate.of(2026, 6, 17), 6, 5, 0)));

        CheckinResponse response = service().checkin(7L);

        assertThat(response.streakDays()).isEqualTo(7);
        assertThat(response.bonusExp()).isEqualTo(20);
        verify(growthService).grantExp(7L, "DAILY_CHECKIN", "DATE", 20260618L, 25);
    }

    @Test
    void missedDayResetsStreakToOne() {
        when(checkinRepository.findByUserIdAndCheckinDate(eq(7L), any())).thenReturn(Optional.empty());
        when(checkinRepository.findTopByUserIdOrderByCheckinDateDesc(7L))
                .thenReturn(Optional.of(checkin(LocalDate.of(2026, 6, 16), 6, 5, 0)));

        assertThat(service().checkin(7L).streakDays()).isEqualTo(1);
    }

    private CheckinService service() {
        return new CheckinService(checkinRepository, userRepository, growthService, clock);
    }

    private DailyCheckin checkin(LocalDate date, int streak, int baseExp, int bonusExp) {
        DailyCheckin item = new DailyCheckin();
        item.setUserId(7L);
        item.setCheckinDate(date);
        item.setStreakDays(streak);
        item.setBaseExp(baseExp);
        item.setBonusExp(bonusExp);
        item.setCreateTime(date.atTime(10, 0));
        return item;
    }
}
```

- [ ] **Step 2: Run tests and confirm the service is missing**

Run: `./mvnw -Dtest=CheckinServiceTest test`

Expected: FAIL to compile because `CheckinService` and response records do not exist.

- [ ] **Step 3: Add an injectable Shanghai clock and per-user database lock**

```java
@Configuration
public class GrowthTimeConfig {
    @Bean
    public Clock growthClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
```

Add to `UserRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select u from User u where u.userId = :userId")
Optional<User> findByIdForUpdate(@Param("userId") Long userId);
```

- [ ] **Step 4: Implement transactional check-in and query methods**

Define the response contracts before the service so frontend field names cannot drift:

```java
public record CheckinResponse(boolean alreadyCheckedIn, LocalDate checkinDate,
        int streakDays, long totalDays, int baseExp, int bonusExp, int totalExp) {}

public record CheckinStatusResponse(boolean checkedInToday, int streakDays,
        long totalDays, int totalExp) {}

public record CheckinCalendarResponse(String month, List<LocalDate> checkedDates) {}
```

The central calculation must remain this small and deterministic:

```java
LocalDate today = LocalDate.now(clock);
DailyCheckin existing = checkinRepository.findByUserIdAndCheckinDate(userId, today).orElse(null);
if (existing != null) return toResponse(existing, true);

DailyCheckin previous = checkinRepository.findTopByUserIdOrderByCheckinDateDesc(userId).orElse(null);
int streak = previous != null && previous.getCheckinDate().equals(today.minusDays(1))
        ? previous.getStreakDays() + 1 : 1;
int bonus = streak % 7 == 0 ? 20 : 0;
DailyCheckin saved = checkinRepository.save(newCheckin(userId, today, streak, 5, bonus));
growthService.grantExp(userId, "DAILY_CHECKIN", "DATE", Long.parseLong(today.format(DATE_KEY)), 5 + bonus);
```

Acquire `findByIdForUpdate(userId)` before the first check so concurrent requests serialize. Add `status(userId)` and `calendar(userId, YearMonth)` methods that never trust a client date and return sorted dates.

- [ ] **Step 5: Run check-in tests**

Run: `./mvnw -Dtest=CheckinServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit the check-in slice**

```bash
git add src/main/java/com/skatehub/config/GrowthTimeConfig.java src/main/java/com/skatehub/pojo/growth src/main/java/com/skatehub/service/CheckinService.java src/main/java/com/skatehub/dao/UserRepository.java src/test/java/com/skatehub/service/CheckinServiceTest.java
git commit -m "feat: add idempotent daily check-in"
```

## Task 3: Total and Weekly Leaderboards

**Files:**
- Create: `src/main/java/com/skatehub/dao/LeaderboardQueryRepository.java`
- Create: `src/main/java/com/skatehub/pojo/growth/LeaderboardEntry.java`
- Create: `src/main/java/com/skatehub/pojo/growth/LeaderboardResponse.java`
- Create: `src/main/java/com/skatehub/service/LeaderboardService.java`
- Test: `src/test/java/com/skatehub/service/LeaderboardServiceTest.java`
- Test: `src/test/java/com/skatehub/growth/LeaderboardQueryContractTest.java`

- [ ] **Step 1: Write failing service tests for type validation and current-user rank**

```java
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {
    @Mock LeaderboardQueryRepository repository;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-18T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    @Test
    void totalBoardReturnsRowsAndCurrentUser() {
        LeaderboardEntry alice = new LeaderboardEntry(1, 7L, "alice", "/a.png", 320, 4);
        when(repository.findTotal(0, 20)).thenReturn(List.of(alice));
        when(repository.countTotal()).thenReturn(1L);
        when(repository.findTotalUser(7L)).thenReturn(Optional.of(alice));

        LeaderboardResponse result = new LeaderboardService(repository, clock).list("total", 1, 20, 7L);

        assertThat(result.list()).containsExactly(alice);
        assertThat(result.currentUser()).isEqualTo(alice);
    }

    @Test
    void unknownBoardTypeIsRejected() {
        assertThatThrownBy(() -> new LeaderboardService(repository, clock).list("daily", 1, 20, 7L))
                .isInstanceOf(BizException.class);
    }
}
```

- [ ] **Step 2: Run and confirm missing leaderboard classes**

Run: `./mvnw -Dtest=LeaderboardServiceTest test`

Expected: FAIL to compile.

- [ ] **Step 3: Implement the isolated MySQL 8 query repository**

Create stable API records first:

```java
public record LeaderboardEntry(long rankNo, Long userId, String username,
        String avatar, int score, int level) {}

public record LeaderboardResponse(long total, List<LeaderboardEntry> list,
        LeaderboardEntry currentUser) {}
```

Expose these repository methods so time-window and pagination inputs are explicit:

```java
List<LeaderboardEntry> findTotal(int offset, int limit);
long countTotal();
Optional<LeaderboardEntry> findTotalUser(Long userId);
List<LeaderboardEntry> findWeekly(LocalDateTime weekStart, LocalDateTime weekEnd, int offset, int limit);
long countWeekly(LocalDateTime weekStart, LocalDateTime weekEnd);
Optional<LeaderboardEntry> findWeeklyUser(Long userId, LocalDateTime weekStart, LocalDateTime weekEnd);
```

Use `NamedParameterJdbcTemplate`. The total board query shape must be:

```sql
SELECT rank_no, user_id, username, avatar, score, level
FROM (
  SELECT ROW_NUMBER() OVER (
           ORDER BY IFNULL(u.exp, 0) DESC,
                    COALESCE(MAX(g.create_time), u.create_time) ASC,
                    u.user_id ASC
         ) AS rank_no,
         u.user_id, u.username, u.avatar, IFNULL(u.exp, 0) AS score,
         LEAST(10, FLOOR(IFNULL(u.exp, 0) / 100) + 1) AS level
  FROM tb_user u
  LEFT JOIN tb_user_growth_log g ON g.user_id = u.user_id
  WHERE u.status = '0'
  GROUP BY u.user_id, u.username, u.avatar, u.exp, u.create_time
) ranked
ORDER BY rank_no
LIMIT :limit OFFSET :offset
```

The weekly query uses the same window shape with `SUM(g.exp_gain)` and filters `g.create_time >= :weekStart AND g.create_time < :weekEnd`. Exclude zero-activity users from the weekly board. Both queries select `LEAST(10, FLOOR(IFNULL(u.exp, 0) / 100) + 1) AS level`. Wrap each ranked query once more with `WHERE user_id = :userId` for current-user rank.

- [ ] **Step 4: Add a SQL contract test**

Expose package-private constants `TOTAL_SQL` and `WEEKLY_SQL`, then assert:

```java
assertThat(LeaderboardQueryRepository.TOTAL_SQL)
        .contains("ROW_NUMBER() OVER", "u.status = '0'", "LIMIT :limit OFFSET :offset");
assertThat(LeaderboardQueryRepository.WEEKLY_SQL)
        .contains("g.create_time >= :weekStart", "g.create_time < :weekEnd", "SUM(g.exp_gain)");
```

- [ ] **Step 5: Implement page validation and level calculation in the service**

Accept only `total` and `weekly`; clamp size through the existing `InputValidator.size` behavior. Map the `level` column selected from current user experience for both boards; never derive the weekly display level from weekly score.

- [ ] **Step 6: Run leaderboard tests**

Run: `./mvnw -Dtest=LeaderboardServiceTest,LeaderboardQueryContractTest test`

Expected: PASS.

- [ ] **Step 7: Commit the leaderboard slice**

```bash
git add src/main/java/com/skatehub/dao/LeaderboardQueryRepository.java src/main/java/com/skatehub/pojo/growth/LeaderboardEntry.java src/main/java/com/skatehub/pojo/growth/LeaderboardResponse.java src/main/java/com/skatehub/service/LeaderboardService.java src/test/java/com/skatehub/service/LeaderboardServiceTest.java src/test/java/com/skatehub/growth/LeaderboardQueryContractTest.java
git commit -m "feat: add growth leaderboards"
```

## Task 4: Badge Progress and Idempotent Awards

**Files:**
- Create: `src/main/java/com/skatehub/pojo/growth/BadgeView.java`
- Create: `src/main/java/com/skatehub/service/BadgeService.java`
- Modify: `src/main/java/com/skatehub/dao/PostRepository.java`
- Modify: `src/main/java/com/skatehub/dao/ActivitySignRepository.java`
- Modify: `src/main/java/com/skatehub/dao/PlaceReviewRepository.java`
- Test: `src/test/java/com/skatehub/service/BadgeServiceTest.java`

- [ ] **Step 1: Write failing progress and duplicate-award tests**

```java
@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {
    @Mock BadgeRepository badgeRepository;
    @Mock UserBadgeRepository userBadgeRepository;
    @Mock DailyCheckinRepository checkinRepository;
    @Mock PostRepository postRepository;
    @Mock ActivitySignRepository activitySignRepository;
    @Mock PlaceReviewRepository reviewRepository;
    @Mock UserRepository userRepository;

    @BeforeEach
    void lockUserBeforeReconcilingAwards() {
        User user = new User();
        user.setUserId(7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void sevenDayStreakUnlocksRareBadgeOnce() {
        Badge badge = badge(2L, "STREAK_7", "RARE");
        when(badgeRepository.findByEnabledOrderBySortOrderAsc("1")).thenReturn(List.of(badge));
        when(checkinRepository.findTopByUserIdOrderByCheckinDateDesc(7L))
                .thenReturn(Optional.of(checkin(LocalDate.now(), 7, 5, 20)));
        when(userBadgeRepository.findByUserIdOrderByUnlockTimeDesc(7L)).thenReturn(List.of());

        List<BadgeView> result = service().evaluateAndList(7L);

        assertThat(result.get(0).unlocked()).isTrue();
        verify(userBadgeRepository).save(argThat(award -> award.getBadgeId().equals(2L)));
    }

    @Test
    void existingAwardIsNotInsertedAgain() {
        Badge badge = badge(2L, "STREAK_7", "RARE");
        when(badgeRepository.findByEnabledOrderBySortOrderAsc("1")).thenReturn(List.of(badge));
        when(userBadgeRepository.findByUserIdOrderByUnlockTimeDesc(7L))
                .thenReturn(List.of(userBadge(7L, 2L)));

        service().evaluateAndList(7L);

        verify(userBadgeRepository, never()).save(any());
    }

    private BadgeService service() {
        return new BadgeService(badgeRepository, userBadgeRepository, checkinRepository,
                postRepository, activitySignRepository, reviewRepository, userRepository);
    }

    private Badge badge(Long id, String code, String rarity) {
        Badge item = new Badge();
        item.setBadgeId(id);
        item.setCode(code);
        item.setName(code);
        item.setDescription(code);
        item.setRarity(rarity);
        item.setIconCode(code.toLowerCase(Locale.ROOT));
        item.setSortOrder(1);
        item.setEnabled("1");
        return item;
    }

    private DailyCheckin checkin(LocalDate date, int streak, int baseExp, int bonusExp) {
        DailyCheckin item = new DailyCheckin();
        item.setUserId(7L);
        item.setCheckinDate(date);
        item.setStreakDays(streak);
        item.setBaseExp(baseExp);
        item.setBonusExp(bonusExp);
        return item;
    }

    private UserBadge userBadge(Long userId, Long badgeId) {
        UserBadge award = new UserBadge();
        award.setUserId(userId);
        award.setBadgeId(badgeId);
        award.setUnlockTime(LocalDateTime.now());
        return award;
    }
}
```

- [ ] **Step 2: Run and confirm `BadgeService` is missing**

Run: `./mvnw -Dtest=BadgeServiceTest test`

Expected: FAIL to compile.

- [ ] **Step 3: Add the missing count queries**

```java
// PostRepository already has countByUserId(Long userId).
long countByUserIdAndIsCheckin(Long userId, String isCheckin); // ActivitySignRepository
long countByUserId(Long userId); // PlaceReviewRepository
```

- [ ] **Step 4: Implement one declarative progress switch**

Use this response record:

```java
public record BadgeView(String code, String name, String description,
        String rarity, String iconCode, boolean unlocked,
        LocalDateTime unlockTime, long current, long target) {}
```

Keep badge conditions in one place and return both progress and target:

```java
private Progress progress(String code, Long userId) {
    return switch (code) {
        case "FIRST_CHECKIN" -> new Progress(checkinRepository.countByUserId(userId), 1);
        case "STREAK_7" -> new Progress(latestStreak(userId), 7);
        case "CHECKIN_30" -> new Progress(checkinRepository.countByUserId(userId), 30);
        case "FIRST_POST" -> new Progress(postRepository.countByUserId(userId), 1);
        case "POST_10" -> new Progress(postRepository.countByUserId(userId), 10);
        case "FIRST_ACTIVITY_CHECKIN" -> new Progress(activitySignRepository.countByUserIdAndIsCheckin(userId, "1"), 1);
        case "ACTIVITY_CHECKIN_3" -> new Progress(activitySignRepository.countByUserIdAndIsCheckin(userId, "1"), 3);
        case "PLACE_REVIEW_3" -> new Progress(reviewRepository.countByUserId(userId), 3);
        default -> new Progress(0, Integer.MAX_VALUE);
    };
}
```

Before saving an unlocked badge, check the loaded award map. Keep the database unique key as the final concurrency guard. `BadgeView` contains code, name, description, rarity, iconCode, unlocked, unlockTime, current, and target.

Inject `UserRepository` and call `findByIdForUpdate(userId)` before reconciling awards. This serializes concurrent GET/event reconciliation for one user so the unique key remains a last-resort invariant rather than normal control flow.

- [ ] **Step 5: Run badge tests**

Run: `./mvnw -Dtest=BadgeServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit badge evaluation**

```bash
git add src/main/java/com/skatehub/pojo/growth/BadgeView.java src/main/java/com/skatehub/service/BadgeService.java src/main/java/com/skatehub/dao/PostRepository.java src/main/java/com/skatehub/dao/ActivitySignRepository.java src/main/java/com/skatehub/dao/PlaceReviewRepository.java src/test/java/com/skatehub/service/BadgeServiceTest.java
git commit -m "feat: add badge progress and awards"
```

## Task 5: Evaluate Badges After Successful Domain Actions

**Files:**
- Create: `src/main/java/com/skatehub/event/GrowthActionCompletedEvent.java`
- Create: `src/main/java/com/skatehub/service/BadgeAwardListener.java`
- Modify: `src/main/java/com/skatehub/service/CheckinService.java`
- Modify: `src/main/java/com/skatehub/service/PostService.java`
- Modify: `src/main/java/com/skatehub/service/ActivityService.java`
- Modify: `src/main/java/com/skatehub/service/PlaceReviewService.java`
- Test: `src/test/java/com/skatehub/service/BadgeAwardListenerTest.java`
- Modify test: `src/test/java/com/skatehub/security/DataOwnershipServiceTest.java`
- Modify test: `src/test/java/com/skatehub/security/InjectionRiskTest.java`
- Test: existing Mockito `@InjectMocks` service tests

- [ ] **Step 1: Write the failing listener isolation test**

```java
@ExtendWith(MockitoExtension.class)
class BadgeAwardListenerTest {
    @Mock BadgeService badgeService;

    @Test
    void completedActionTriggersBestEffortEvaluation() {
        BadgeAwardListener listener = new BadgeAwardListener(badgeService);
        listener.onGrowthAction(new GrowthActionCompletedEvent(7L, "POST_CREATE"));
        verify(badgeService).evaluateAndList(7L);
    }

    @Test
    void badgeFailureDoesNotEscapeListener() {
        doThrow(new RuntimeException("award unavailable")).when(badgeService).evaluateAndList(7L);
        assertThatCode(() -> new BadgeAwardListener(badgeService)
                .onGrowthAction(new GrowthActionCompletedEvent(7L, "POST_CREATE")))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run and confirm missing event/listener failure**

Run: `./mvnw -Dtest=BadgeAwardListenerTest test`

Expected: FAIL to compile.

- [ ] **Step 3: Implement after-commit badge evaluation**

```java
public record GrowthActionCompletedEvent(Long userId, String actionType) {}

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeAwardListener {
    private final BadgeService badgeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrowthAction(GrowthActionCompletedEvent event) {
        try {
            badgeService.evaluateAndList(event.userId());
        } catch (RuntimeException ex) {
            log.warn("Badge evaluation failed for user {} after {}", event.userId(), event.actionType(), ex);
        }
    }
}
```

Mark `BadgeService.evaluateAndList` with `@Transactional(propagation = Propagation.REQUIRES_NEW)` so the listener's write is independent.

- [ ] **Step 4: Publish only after successful writes**

Inject `ApplicationEventPublisher` and add one publication after each existing successful save/grant boundary:

```java
eventPublisher.publishEvent(new GrowthActionCompletedEvent(currentUserId, "POST_CREATE"));
eventPublisher.publishEvent(new GrowthActionCompletedEvent(currentUserId, "ACTIVITY_CHECKIN"));
eventPublisher.publishEvent(new GrowthActionCompletedEvent(currentUser.id(), "PLACE_REVIEW_CREATE"));
eventPublisher.publishEvent(new GrowthActionCompletedEvent(userId, "DAILY_CHECKIN"));
```

Annotate `PostService.create`, `ActivityService.checkin`, and `PlaceReviewService.create` with `@Transactional`; `CheckinService.checkin` is already transactional. This ensures `AFTER_COMMIT` listeners run only after the primary action commits. Do not publish for activity registration; only `ActivityService.checkin` qualifies.

In `DataOwnershipServiceTest` and `InjectionRiskTest`, append `mock(ApplicationEventPublisher.class)` to each manually constructed `PostService` or `ActivityService`. Mockito `@InjectMocks` tests need an added `@Mock ApplicationEventPublisher eventPublisher` field only when injection reports the new dependency.

- [ ] **Step 5: Run focused and existing service tests**

Run: `./mvnw -Dtest=BadgeAwardListenerTest,ActivityServiceExpiryTest,ActivityServiceContentTest,DataOwnershipServiceTest,InjectionRiskTest,PasswordPolicyIntegrationTest test`

Expected: PASS.

- [ ] **Step 6: Commit domain integration**

```bash
git add src/main/java/com/skatehub/event/GrowthActionCompletedEvent.java src/main/java/com/skatehub/service/BadgeAwardListener.java src/main/java/com/skatehub/service/CheckinService.java src/main/java/com/skatehub/service/PostService.java src/main/java/com/skatehub/service/ActivityService.java src/main/java/com/skatehub/service/PlaceReviewService.java src/test/java/com/skatehub/service/BadgeAwardListenerTest.java src/test/java/com/skatehub/security/DataOwnershipServiceTest.java src/test/java/com/skatehub/security/InjectionRiskTest.java
git commit -m "feat: evaluate badges after user actions"
```

## Task 6: Authenticated Growth API

**Files:**
- Modify: `src/main/java/com/skatehub/controller/UserController.java`
- Test: `src/test/java/com/skatehub/controller/UserGrowthControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests for authentication and parameter validation**

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserGrowthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean UserService userService;
    @MockBean ReportService reportService;
    @MockBean PlaceReviewService placeReviewService;
    @MockBean RecommendationService recommendationService;
    @MockBean CheckinService checkinService;
    @MockBean LeaderboardService leaderboardService;
    @MockBean BadgeService badgeService;

    @Test
    void anonymousCheckinIsUnauthorized() throws Exception {
        mvc.perform(post("/api/user/checkin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidCalendarMonthIsBadRequest() throws Exception {
        mvc.perform(get("/api/user/checkin/calendar").param("month", "2026-13"))
                .andExpect(status().isBadRequest());
    }
}
```

Keep the real `SecurityConfig` in this slice and mock its `JwtAuthenticationFilter` dependency as shown. Retain the existing `SecurityBoundaryTest` alongside this test so both runtime authorization rules and controller validation are covered.

- [ ] **Step 2: Add controller dependencies and five endpoints**

```java
@PostMapping("/checkin")
public ApiResponse<CheckinResponse> checkin() {
    return ApiResponse.success(checkinService.checkin(SecurityUtils.currentUser().id()));
}

@GetMapping("/checkin/status")
public ApiResponse<CheckinStatusResponse> checkinStatus() {
    return ApiResponse.success(checkinService.status(SecurityUtils.currentUser().id()));
}

@GetMapping("/checkin/calendar")
public ApiResponse<CheckinCalendarResponse> checkinCalendar(
        @RequestParam @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String month) {
    return ApiResponse.success(checkinService.calendar(
            SecurityUtils.currentUser().id(), YearMonth.parse(month)));
}

@GetMapping("/leaderboard")
public ApiResponse<LeaderboardResponse> leaderboard(
        @RequestParam @Pattern(regexp = "total|weekly") String type,
        @RequestParam(defaultValue = "1") @Positive int page,
        @RequestParam(defaultValue = "20") @Positive int size) {
    Long userId = SecurityUtils.currentUser().id();
    return ApiResponse.success(leaderboardService.list(type, page, size, userId));
}

@GetMapping("/badges")
public ApiResponse<List<BadgeView>> badges() {
    return ApiResponse.success(badgeService.evaluateAndList(SecurityUtils.currentUser().id()));
}
```

- [ ] **Step 3: Run controller and security tests**

Run: `./mvnw -Dtest=UserGrowthControllerTest,SecurityBoundaryTest test`

Expected: PASS.

- [ ] **Step 4: Commit the API slice**

```bash
git add src/main/java/com/skatehub/controller/UserController.java src/test/java/com/skatehub/controller/UserGrowthControllerTest.java
git commit -m "feat: expose authenticated growth APIs"
```

## Task 7: Growth Center Data Flow and Layout

**Files:**
- Modify: `frontend/src/api/index.js`
- Create: `frontend/src/components/growth/GrowthCenterPanel.vue`
- Modify: `frontend/src/views/ProfilePage.vue`
- Test: `frontend/tests/growthCenter.test.cjs`

- [ ] **Step 1: Write a failing frontend contract test**

```javascript
const assert = require('assert')
const fs = require('fs')
const path = require('path')

const api = fs.readFileSync(path.resolve(__dirname, '../src/api/index.js'), 'utf8')
const profile = fs.readFileSync(path.resolve(__dirname, '../src/views/ProfilePage.vue'), 'utf8')
const panelPath = path.resolve(__dirname, '../src/components/growth/GrowthCenterPanel.vue')

assert.match(api, /dailyCheckin:.*\/api\/user\/checkin/)
assert.match(api, /growthLeaderboard:.*\/api\/user\/leaderboard/)
assert.match(profile, /GrowthCenterPanel/)
assert.match(profile, /profileTab === 'growth'/)
assert.ok(fs.existsSync(panelPath), 'growth center component exists')
```

- [ ] **Step 2: Run and confirm the missing API/component failure**

Run: `node frontend/tests/growthCenter.test.cjs`

Expected: FAIL.

- [ ] **Step 3: Add API methods**

```javascript
dailyCheckin: () => http.post('/api/user/checkin'),
checkinStatus: () => http.get('/api/user/checkin/status'),
checkinCalendar: (month) => http.get('/api/user/checkin/calendar', { params: { month } }),
growthLeaderboard: (params) => http.get('/api/user/leaderboard', { params }),
badges: () => http.get('/api/user/badges'),
```

- [ ] **Step 4: Build `GrowthCenterPanel.vue` with isolated loading states**

The component owns `status`, `calendar`, `leaderboard`, `badges`, `boardType`, and independent error refs. Load summary data together, but let a leaderboard failure leave check-in usable:

```javascript
async function loadSummary() {
  summaryLoading.value = true
  try {
    const [statusData, calendarData, badgeData] = await Promise.all([
      api.checkinStatus(), api.checkinCalendar(monthKey.value), api.badges()
    ])
    status.value = statusData
    calendar.value = calendarData
    badges.value = badgeData || []
  } catch (error) {
    summaryError.value = error?.message || '成长数据加载失败，请稍后重试'
  } finally {
    summaryLoading.value = false
  }
}
```

`submitCheckin` disables while pending, calls `dailyCheckin`, reloads summary and leaderboard, and emits `experience-changed` so `ProfilePage.vue` can call its existing `loadProfile()`.

Build the month grid without a date library:

```javascript
const calendarDays = computed(() => {
  const [year, month] = monthKey.value.split('-').map(Number)
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const count = new Date(year, month, 0).getDate()
  const checked = new Set(calendar.value?.checkedDates || [])
  return [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: count }, (_, index) => {
      const day = index + 1
      const date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
      return { day, date, checked: checked.has(date) }
    })
  ]
})
```

- [ ] **Step 5: Mount one new profile tab**

Add the import, a `成长中心` tab button, and:

```vue
<GrowthCenterPanel
  v-if="profileTab === 'growth'"
  :profile="profile"
  @experience-changed="loadProfile"
/>
```

Do not move existing profile logic or styles.

- [ ] **Step 6: Run the contract test and production build**

Run: `node frontend/tests/growthCenter.test.cjs`

Expected: PASS.

Run: `npm --prefix frontend run build`

Expected: build completes without Vue compiler errors.

- [ ] **Step 7: Commit the growth center shell**

```bash
git add frontend/src/api/index.js frontend/src/components/growth/GrowthCenterPanel.vue frontend/src/views/ProfilePage.vue frontend/tests/growthCenter.test.cjs
git commit -m "feat: add profile growth center"
```

## Task 8: Street Badge Artwork and Unlock Presentation

**Files:**
- Create: `frontend/src/constants/badges.js`
- Create: `frontend/src/components/growth/AchievementBadge.vue`
- Create: `frontend/src/components/growth/BadgeUnlockModal.vue`
- Modify: `frontend/src/components/growth/GrowthCenterPanel.vue`
- Test: `frontend/tests/achievementBadgeStyle.test.cjs`

- [ ] **Step 1: Write the failing visual contract test**

```javascript
const assert = require('assert')
const fs = require('fs')
const path = require('path')

const badge = fs.readFileSync(path.resolve(__dirname, '../src/components/growth/AchievementBadge.vue'), 'utf8')
const modal = fs.readFileSync(path.resolve(__dirname, '../src/components/growth/BadgeUnlockModal.vue'), 'utf8')
const paths = fs.readFileSync(path.resolve(__dirname, '../src/constants/badges.js'), 'utf8')

assert.match(badge, /polygon\(50% 0, 94% 18%, 86% 72%/)
assert.match(badge, /COMMON[\s\S]*RARE[\s\S]*EPIC[\s\S]*LEGENDARY/)
assert.match(paths, /streak-seven[\s\S]*activity-three[\s\S]*place-explorer/)
assert.match(modal, /role="dialog"/)
assert.match(modal, /prefers-reduced-motion/)
```

- [ ] **Step 2: Run and confirm missing component failure**

Run: `node frontend/tests/achievementBadgeStyle.test.cjs`

Expected: FAIL because the files do not exist.

- [ ] **Step 3: Add local hand-drawn path data for all eight icons**

Export a plain map so artwork stays data-driven:

```javascript
export const badgeIconPaths = {
  'first-checkin': ['M18 34l10 10 20-24', 'M16 14c18-9 34 4 32 20'],
  'streak-seven': ['M32 8c8 12 15 18 15 29a15 15 0 1 1-30 0c0-8 5-14 12-22'],
  'checkin-thirty': ['M14 18h36v32H14z', 'M20 10v14M44 10v14M14 28h36', 'M23 37h6M35 37h6M23 45h6'],
  'first-post': ['M12 14h40v30H30L18 53v-9h-6z', 'M21 24h22M21 33h15'],
  'post-ten': ['M11 18h30v24H25L16 49v-7h-5z', 'M28 11h25v22h-8', 'M18 27h16'],
  'first-activity': ['M14 17h36v34H14z', 'M21 10v14M43 10v14M14 27h36', 'M23 36l6 6 13-14'],
  'activity-three': ['M10 40c8-9 14-12 22-12s14 3 22 12', 'M18 49h28M14 54h36', 'M20 20a7 7 0 1 0 0-14M44 20a7 7 0 1 0 0-14'],
  'place-explorer': ['M32 54s17-16 17-29a17 17 0 1 0-34 0c0 13 17 29 17 29z', 'M32 31a7 7 0 1 0 0-14 7 7 0 0 0 0 14z']
}
```

- [ ] **Step 4: Implement the irregular shield and rarity palette**

Use a stable class map:

```javascript
const rarityClass = {
  COMMON: 'is-common',
  RARE: 'is-rare',
  EPIC: 'is-epic',
  LEGENDARY: 'is-legendary'
}
```

The component renders local paths in a `viewBox="0 0 64 64"` SVG. CSS uses the approved shield polygon, white sticker border, offset color shadow, and these colors: common `#cbd5e1`, rare `#60a5fa`, epic `#c084fc`, legendary `#fbbf24`. Locked badges use grayscale, reduced opacity, and no rarity glow.

- [ ] **Step 5: Implement accessible unlock modal behavior**

`BadgeUnlockModal` accepts one badge and emits `close`. It uses `role="dialog"`, `aria-modal="true"`, a labelled heading, close button, Escape handling, and restores focus to the triggering element. Disable entrance transforms inside:

```css
@media (prefers-reduced-motion: reduce) {
  .unlock-card, .unlock-rays { animation: none !important; transition: none !important; }
}
```

In `GrowthCenterPanel`, compare badge codes before and after check-in; queue newly unlocked badges and show them one at a time.

- [ ] **Step 6: Run visual contract and build**

Run: `node frontend/tests/achievementBadgeStyle.test.cjs`

Expected: PASS.

Run: `npm --prefix frontend run build`

Expected: PASS.

- [ ] **Step 7: Commit badge presentation**

```bash
git add frontend/src/constants/badges.js frontend/src/components/growth/AchievementBadge.vue frontend/src/components/growth/BadgeUnlockModal.vue frontend/src/components/growth/GrowthCenterPanel.vue frontend/tests/achievementBadgeStyle.test.cjs
git commit -m "feat: add street-style achievement badges"
```

## Task 9: Full Verification and Deployment SQL Check

**Files:**
- Modify only if verification exposes a defect in files already touched by Tasks 1-8.

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test`

Expected: BUILD SUCCESS with all JUnit tests passing.

- [ ] **Step 2: Run every frontend contract test**

Run:

```powershell
Get-ChildItem frontend/tests/*.test.cjs | ForEach-Object { node $_.FullName; if ($LASTEXITCODE -ne 0) { throw "Test failed: $($_.Name)" } }
```

Expected: command completes without an exception.

- [ ] **Step 3: Run a clean frontend production build**

Run: `npm --prefix frontend run build`

Expected: `DONE Build complete` and exit code 0.

- [ ] **Step 4: Validate the migration against a disposable or development MySQL database**

Run with the configured development credentials:

```powershell
mysql -u $env:DB_USERNAME -p$env:DB_PASSWORD skate_exchange < database/add-growth-achievement-system.sql
mysql -u $env:DB_USERNAME -p$env:DB_PASSWORD skate_exchange -e "SELECT code, rarity FROM tb_badge ORDER BY sort_order;"
```

Expected: migration can be run twice without duplicate rows, and the query returns exactly eight badge codes.

- [ ] **Step 5: Perform the approved acceptance flow**

1. Sign in as a normal user and open Personal Center > Growth Center.
2. Check in once and verify `+5` experience, calendar mark, updated level progress, and disabled checked-in button.
3. Submit the same check-in request again from the browser network panel and verify no additional experience.
4. Use test data to reach a seventh consecutive day and verify `+20` bonus.
5. Confirm total and weekly rank tabs, current-user highlighting, and stable pagination.
6. Complete an eligible action and verify the street shield unlock dialog and persisted unlocked state.
7. Verify locked badges show progress and reveal no sensitive user data.

- [ ] **Step 6: Inspect the final diff and commit only verification fixes**

Run: `git status --short` and `git diff --check`.

Expected: no whitespace errors and no generated `dist`, logs, `.playwright-cli`, output screenshots, or `.superpowers` files staged.

If Task 9 required fixes, stage each already-touched file by its exact path after reviewing `git diff`, then commit:

```bash
git commit -m "fix: finalize growth achievement system"
```
