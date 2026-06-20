# Growth Badge Gallery And Level Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a complete badge gallery and a Lv1-Lv10 skateboard-park progression route inside the existing growth center without changing experience or badge-award rules.

**Architecture:** Keep `GrowthCenterPanel` as the subview shell and summary owner. Move the gallery and roadmap into focused child components, keep the existing badge API, and add one authenticated endpoint as the single source of truth for level titles, thresholds, descriptions, and current state.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring MVC/Security, JUnit 5/Mockito/MockMvc, Vue 3 Composition API, scoped CSS, Node source-contract tests, Vue SFC compiler.

---

## File Map

- Create `src/main/java/com/skatehub/pojo/growth/LevelRoadmapItem.java`: one route node.
- Create `src/main/java/com/skatehub/pojo/growth/LevelRoadmapResponse.java`: current progress and ten nodes.
- Create `frontend/src/components/growth/BadgeDetailDrawer.vue`: accessible badge detail overlay.
- Create `frontend/src/components/growth/BadgeGalleryPanel.vue`: statistics, filters, cards, and detail state.
- Create `frontend/src/components/growth/LevelRoadmapPanel.vue`: identity summary and responsive route.
- Create `frontend/tests/growthBadgeGallery.test.cjs`: gallery and drawer contracts.
- Create `frontend/tests/growthLevelRoadmap.test.cjs`: API and route contracts.
- Modify `UserGrowthService`, `UserController`, their focused tests, `frontend/src/api/index.js`, `GrowthCenterPanel.vue`, and existing growth frontend tests.

No database file changes. Fixed titles and descriptions remain application configuration for this release.

---

### Task 1: Build The Backend Level Roadmap Domain

**Files:**
- Create: `src/main/java/com/skatehub/pojo/growth/LevelRoadmapItem.java`
- Create: `src/main/java/com/skatehub/pojo/growth/LevelRoadmapResponse.java`
- Modify: `src/main/java/com/skatehub/service/UserGrowthService.java`
- Test: `src/test/java/com/skatehub/service/UserGrowthServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Add tests that stub `userRepository.findById(USER_ID)` and assert the response for 215 EXP:

```java
@Test
void roadmapUsesFixedTitlesAndThreeExplicitStates() {
    user.setExp(215);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    LevelRoadmapResponse result = service.levelRoadmap(USER_ID);
    assertEquals(3, result.currentLevel());
    assertEquals(4, result.nextLevel());
    assertEquals(85, result.remainToNextLevel());
    assertEquals(10, result.levels().size());
    assertEquals("新板入场", result.levels().get(0).title());
    assertEquals("COMPLETED", result.levels().get(0).state());
    assertEquals("街角常客", result.levels().get(2).title());
    assertEquals("CURRENT", result.levels().get(2).state());
    assertEquals("LOCKED", result.levels().get(3).state());
    assertEquals("滑行传奇", result.levels().get(9).title());
    assertEquals(900, result.levels().get(9).requiredExp());
}

@Test
void roadmapKeepsExistingBoundariesAndStopsAtLevelTen() {
    int[][] cases = {{0,1}, {99,1}, {100,2}, {899,9}, {900,10}, {1200,10}};
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    for (int[] item : cases) {
        user.setExp(item[0]);
        assertEquals(item[1], service.levelRoadmap(USER_ID).currentLevel(), "exp=" + item[0]);
    }
    user.setExp(900);
    LevelRoadmapResponse max = service.levelRoadmap(USER_ID);
    assertNull(max.nextLevel());
    assertEquals(0, max.remainToNextLevel());
}
```

- [ ] **Step 2: Verify RED**

Run `.\mvnw.cmd -Dtest=UserGrowthServiceTest test`.

Expected: compilation fails because the records and `levelRoadmap` do not exist.

- [ ] **Step 3: Add immutable response records**

```java
package com.skatehub.pojo.growth;

public record LevelRoadmapItem(int level, String title, int requiredExp, String state, String description) {}
```

```java
package com.skatehub.pojo.growth;

import java.util.List;

public record LevelRoadmapResponse(
        int currentLevel, int currentExp, Integer nextLevel,
        int remainToNextLevel, List<LevelRoadmapItem> levels) {}
```

- [ ] **Step 4: Implement `UserGrowthService.levelRoadmap`**

Add the confirmed title list and matching stage descriptions. Query `userRepository.findById`, normalize negative/null EXP to zero, call the existing `calculateLevel` and `expForLevel`, set `nextLevel` to null at Lv10, and create ten nodes with `COMPLETED`, `CURRENT`, or `LOCKED`.

```java
private static final List<String> LEVEL_TITLES = List.of(
        "新板入场", "板感初醒", "街角常客", "路线探索", "街式新锐",
        "地形玩家", "场地焦点", "城市领滑", "街头主理", "滑行传奇");

private static final List<String> LEVEL_DESCRIPTIONS = List.of(
        "迈出第一步，开始记录你的滑行成长。",
        "持续活跃，让每一次上板都更有感觉。",
        "你已经成为街角常见的活跃身影。",
        "探索更多内容、活动与滑板场地。",
        "稳定积累，在社区中展现自己的风格。",
        "尝试不同场景，拓宽你的滑行地图。",
        "你的活跃开始吸引更多板友注意。",
        "带动交流，成为城市滑板社区的领滑者。",
        "持续贡献，形成属于自己的街头影响力。",
        "抵达当前成长路线的最高阶段。");

public LevelRoadmapResponse levelRoadmap(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("User not found"));
    int exp = Math.max(0, user.getExp() == null ? 0 : user.getExp());
    int current = calculateLevel(exp);
    Integer next = current >= 10 ? null : current + 1;
    int remain = next == null ? 0 : Math.max(0, expForLevel(next) - exp);
    List<LevelRoadmapItem> levels = IntStream.rangeClosed(1, 10)
            .mapToObj(level -> new LevelRoadmapItem(
                    level, LEVEL_TITLES.get(level - 1), expForLevel(level),
                    level < current ? "COMPLETED" : level == current ? "CURRENT" : "LOCKED",
                    LEVEL_DESCRIPTIONS.get(level - 1)))
            .toList();
    return new LevelRoadmapResponse(current, exp, next, remain, levels);
}
```

Do not change `grantExp`, `fillGrowthInfo`, `calculateLevel`, or the 100-EXP formula.

- [ ] **Step 5: Verify GREEN and commit**

Run `.\mvnw.cmd -Dtest=UserGrowthServiceTest test`; expect all tests to pass.

```powershell
git add -- src/main/java/com/skatehub/pojo/growth/LevelRoadmapItem.java src/main/java/com/skatehub/pojo/growth/LevelRoadmapResponse.java src/main/java/com/skatehub/service/UserGrowthService.java src/test/java/com/skatehub/service/UserGrowthServiceTest.java
git commit -m "feat: add level roadmap domain"
```

---

### Task 2: Expose The User-Only API

Endpoint contract: `GET /api/user/level-roadmap` returns the authenticated user's complete route.

**Files:**
- Modify: `src/main/java/com/skatehub/controller/UserController.java`
- Modify: `src/test/java/com/skatehub/controller/UserGrowthControllerTest.java`
- Modify: `frontend/src/api/index.js`
- Modify: `frontend/tests/growthCenter.test.cjs`

- [ ] **Step 1: Write failing controller tests**

Add `@MockBean UserGrowthService`. Cover anonymous 401, admin 403, and an authenticated response containing current level, EXP, next level, remaining EXP, title, threshold, state, and description.

```java
@Test
void levelRoadmapUsesAuthenticatedUser() throws Exception {
    LevelRoadmapResponse response = new LevelRoadmapResponse(3, 215, 4, 85,
            List.of(new LevelRoadmapItem(3, "街角常客", 200, "CURRENT", "街角活跃身份。")));
    when(userGrowthService.levelRoadmap(USER_ID)).thenReturn(response);
    mockMvc.perform(get("/api/user/level-roadmap").with(currentUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentLevel").value(3))
            .andExpect(jsonPath("$.data.levels[0].title").value("街角常客"))
            .andExpect(jsonPath("$.data.levels[0].state").value("CURRENT"));
    verify(userGrowthService).levelRoadmap(USER_ID);
}
```

- [ ] **Step 2: Verify RED**

Run `.\mvnw.cmd -Dtest=UserGrowthControllerTest test`.

Expected: authenticated request returns 404.

- [ ] **Step 3: Add the endpoint**

Inject `UserGrowthService` and add:

```java
@GetMapping("/level-roadmap")
@PreAuthorize("hasRole('USER')")
public ApiResponse<LevelRoadmapResponse> levelRoadmap() {
    CurrentUser currentUser = SecurityUtils.currentUser();
    return ApiResponse.success(userGrowthService.levelRoadmap(currentUser.id()));
}
```

- [ ] **Step 4: Add and satisfy the frontend API contract**

First assert in `growthCenter.test.cjs` that compacted API source contains `levelRoadmap:()=>http.get('/api/user/level-roadmap')`; run the test and observe failure. Then add:

```javascript
levelRoadmap: () => http.get('/api/user/level-roadmap'),
```

- [ ] **Step 5: Verify and commit**

Run `.\mvnw.cmd -Dtest=UserGrowthControllerTest test` and `node frontend/tests/growthCenter.test.cjs`; both must pass.

```powershell
git add -- src/main/java/com/skatehub/controller/UserController.java src/test/java/com/skatehub/controller/UserGrowthControllerTest.java frontend/src/api/index.js frontend/tests/growthCenter.test.cjs
git commit -m "feat: expose level roadmap endpoint"
```

---

### Task 3: Add The Accessible Badge Detail Drawer

**Files:**
- Create: `frontend/src/components/growth/BadgeDetailDrawer.vue`
- Create: `frontend/tests/growthBadgeGallery.test.cjs`
- Modify: `frontend/tests/achievementBadgeStyle.test.cjs`

- [ ] **Step 1: Write the failing contract**

Create a Node source test that asserts the file exists and contains `Teleport`, `role="dialog"`, `aria-modal`, Escape handling, focus trapping, `returnFocusTo`, body-scroll restoration, a 640px bottom-sheet media query, and reduced-motion handling.

Run `node frontend/tests/growthBadgeGallery.test.cjs`.

Expected: FAIL because the component is missing.

- [ ] **Step 2: Implement the drawer**

Follow `BadgeUnlockModal.vue` for focus trap and cleanup. Props are `badge` and `returnFocusTo`; emit `close`. Render `AchievementBadge` at 128px, title, rarity, description, progressbar for locked badges, and unlock time for unlocked badges.

```vue
<Teleport to="body">
  <div class="badge-drawer-backdrop" @mousedown.self="$emit('close')">
    <aside ref="dialog" class="badge-drawer" role="dialog" aria-modal="true" :aria-labelledby="`badge-detail-${badge.code}`">
      <button type="button" aria-label="关闭徽章详情" @click="$emit('close')">关闭</button>
      <AchievementBadge :badge="badge" :size="128" />
      <p>BADGE ARCHIVE / 徽章档案</p>
      <h2 :id="`badge-detail-${badge.code}`">{{ badge.name }}</h2>
      <p>{{ badge.description }}</p>
      <div v-if="!badge.unlocked" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
        <span :style="{ width: `${progress}%` }"></span>
      </div>
      <p v-else>获得时间：{{ formattedUnlockTime }}</p>
    </aside>
  </div>
</Teleport>
```

Desktop uses a right drawer; at 640px it becomes a bottom sheet. Both constrain height to `100dvh`, scroll internally, and disable transitions under reduced motion.

- [ ] **Step 3: Extend accessibility checks and verify**

Add the new drawer to `achievementBadgeStyle.test.cjs` and repeat the existing modal checks for semantics, Escape, focus return, and scroll cleanup.

Run both Node tests; expect exit 0.

- [ ] **Step 4: Commit**

```powershell
git add -- frontend/src/components/growth/BadgeDetailDrawer.vue frontend/tests/growthBadgeGallery.test.cjs frontend/tests/achievementBadgeStyle.test.cjs
git commit -m "feat: add badge detail drawer"
```

---

### Task 4: Build The Filterable Badge Gallery

**Files:**
- Create: `frontend/src/components/growth/BadgeGalleryPanel.vue`
- Modify: `frontend/tests/growthBadgeGallery.test.cjs`

- [ ] **Step 1: Add failing gallery contracts**

Assert the new component receives `badges`, owns `statusFilter` and `rarityFilter`, derives `filteredBadges`, `unlockedCount`, completion and rarity counts, announces results with `aria-live="polite"`, renders `BadgeDetailDrawer`, and has 4/2/1-column responsive rules.

Run the test; expect failure because the gallery is absent.

- [ ] **Step 2: Implement filtering and sorting**

```javascript
const statusFilter = ref('ALL')
const rarityFilter = ref('ALL')
const selectedBadge = ref(null)
const detailTrigger = ref(null)
const rarityOrder = { LEGENDARY: 0, EPIC: 1, RARE: 2, COMMON: 3 }
const unlockedCount = computed(() => props.badges.filter(item => item.unlocked).length)
const completion = computed(() => props.badges.length ? Math.round(unlockedCount.value / props.badges.length * 100) : 0)
const filteredBadges = computed(() => [...props.badges]
  .filter(item => statusFilter.value === 'ALL' || (statusFilter.value === 'UNLOCKED') === Boolean(item.unlocked))
  .filter(item => rarityFilter.value === 'ALL' || item.rarity === rarityFilter.value)
  .sort((a, b) => Number(b.unlocked) - Number(a.unlocked) || rarityOrder[a.rarity] - rarityOrder[b.rarity]))
```

Render the black/yellow archive header, four rarity totals, status and rarity button groups with `aria-pressed`, result count, cards, locked progress, unlocked stamp/date, clear-filter empty state, and the detail drawer with trigger focus return.

- [ ] **Step 3: Add responsive street-archive styling**

Use existing paper texture and rarity colors. Use four columns above 1100px, three from 780px, two from 560px, and one below 560px. Keep visible focus rings and do not use color alone for lock state.

- [ ] **Step 4: Verify and commit**

Run the gallery test and parse both new Vue files with `@vue/compiler-sfc`; expect contracts and syntax to pass.

```powershell
git add -- frontend/src/components/growth/BadgeGalleryPanel.vue frontend/tests/growthBadgeGallery.test.cjs
git commit -m "feat: add filterable badge gallery"
```

---

### Task 5: Build The Responsive Level Route

**Files:**
- Create: `frontend/src/components/growth/LevelRoadmapPanel.vue`
- Create: `frontend/tests/growthLevelRoadmap.test.cjs`

- [ ] **Step 1: Write the failing route contract**

Assert the component receives `roadmap`, `loading`, and `error`; renders all backend levels; represents `COMPLETED`, `CURRENT`, and `LOCKED`; exposes current with `aria-current="step"`; shows remaining EXP or max-level copy; hides horizontal overflow; switches layout at 700px; and honors reduced motion.

Run the test; expect failure because the component is absent.

- [ ] **Step 2: Implement the route**

Track `selectedLevel`, default it to the current node when `roadmap.levels` changes, and render:

```vue
<header class="roadmap-identity">
  <p>LEVEL EVOLUTION / 等级进化</p>
  <h2>Lv{{ roadmap.currentLevel }} {{ currentNode?.title }}</h2>
  <strong>{{ roadmap.currentExp }} EXP</strong>
  <span v-if="roadmap.nextLevel">距离 Lv{{ roadmap.nextLevel }} 还需 {{ roadmap.remainToNextLevel }} EXP</span>
  <span v-else>已抵达当前最高等级</span>
</header>
<div class="skatepark-route">
  <button v-for="level in roadmap.levels" :key="level.level"
    :class="['level-node', level.state.toLowerCase()]"
    :aria-current="level.state === 'CURRENT' ? 'step' : undefined"
    :aria-pressed="selectedLevel?.level === level.level"
    @click="selectedLevel = level">
    <span>LV.{{ level.level }}</span><strong>{{ level.title }}</strong><small>{{ level.requiredExp }} EXP</small>
  </button>
</div>
```

Add loading, alert/retry, empty, and selected-node detail states. Desktop is a five-column, two-row zigzag skatepark route; mobile is a vertical left-rail route. Add textual state labels and disable current-node pulse under reduced motion.

- [ ] **Step 3: Verify and commit**

Run the route Node test and parse the SFC; both must pass.

```powershell
git add -- frontend/src/components/growth/LevelRoadmapPanel.vue frontend/tests/growthLevelRoadmap.test.cjs
git commit -m "feat: add skateboard level route"
```

---

### Task 6: Integrate Growth Center Subviews

**Files:**
- Modify: `frontend/src/components/growth/GrowthCenterPanel.vue`
- Modify: `frontend/tests/growthCenter.test.cjs`

- [ ] **Step 1: Add failing integration contracts**

Assert imports for both panels, `growthView = ref('summary')`, labels for all three subviews, active `aria-current`, independent `levelRoadmapLoading` and `levelRoadmapError`, `loadLevelRoadmap`, `recentUnlockedBadges`, a shared `growth-view-host`, 280ms and 10px transition values, and reduced-motion behavior.

Run `node frontend/tests/growthCenter.test.cjs`; expect failure on missing integration.

- [ ] **Step 2: Add independent roadmap loading**

Add a latest-request gate and call the endpoint independently on mount:

```javascript
const growthView = ref('summary')
const growthViews = [
  { key: 'summary', label: '成长总览' },
  { key: 'badges', label: '徽章图鉴' },
  { key: 'roadmap', label: '等级路线' }
]
const levelRoadmap = ref({ levels: [] })
const levelRoadmapLoading = ref(false)
const levelRoadmapError = ref('')
const levelRoadmapGate = createLatestRequestGate()
const recentUnlockedBadges = computed(() => badgeList.value
  .filter(item => item.unlocked)
  .sort((a, b) => String(b.unlockTime || '').localeCompare(String(a.unlockTime || '')))
  .slice(0, 3))
```

`loadLevelRoadmap` captures a gate token, sets only roadmap loading/error state, calls `api.levelRoadmap`, ignores stale responses, and exposes retry. Invalidate the gate on unmount.

- [ ] **Step 3: Add measured subview switching**

Reuse the proven profile-tab algorithm locally: capture current host height, cancel prior timer/frame, set fixed height, update view, await `nextTick`, measure the new first child, animate to its height for 280ms, then clear inline height/overflow. Same-view clicks return; reduced motion switches immediately; unmount cleans up.

```vue
<nav class="growth-subnav" aria-label="成长中心页面">
  <button v-for="item in growthViews" :key="item.key"
    :aria-current="growthView === item.key ? 'page' : undefined"
    @click="switchGrowthView(item.key)">{{ item.label }}</button>
</nav>
<div ref="growthViewHost" class="growth-view-host">
  <BadgeGalleryPanel v-if="growthView === 'badges'" class="growth-view" :badges="badgeList" />
  <LevelRoadmapPanel v-else-if="growthView === 'roadmap'" class="growth-view" :roadmap="levelRoadmap"
    :loading="levelRoadmapLoading" :error="levelRoadmapError" @retry="loadLevelRoadmap" />
</div>
```

Place the existing experience, check-in, calendar, and leaderboard markup in a sibling `<div v-if="growthView === 'summary'" class="growth-view">` inside the same host, before the two child panels. Replace the full summary badge wall with the three-item `recentUnlockedBadges` preview and a “查看全部徽章” action.

- [ ] **Step 4: Add full-width transition styles**

```css
.growth-view-host, .growth-view { width: 100%; min-width: 0; }
.growth-view-host { transition: height 280ms cubic-bezier(.22,1,.36,1); }
.growth-view-host.is-switching { overflow: hidden; }
.growth-view-host.is-entering > .growth-view { animation: growth-view-enter 280ms cubic-bezier(.22,1,.36,1) both; }
@keyframes growth-view-enter { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .growth-view-host { transition: none; } .growth-view-host.is-entering > .growth-view { animation: none; } }
```

Add a horizontally scrollable subnav on narrow screens.

- [ ] **Step 5: Verify and commit**

Run `growthCenter`, `achievementBadgeStyle`, `growthBadgeGallery`, and `growthLevelRoadmap` Node tests, then parse all four Vue components with `@vue/compiler-sfc`. Expect all checks to pass.

```powershell
git add -- frontend/src/components/growth/GrowthCenterPanel.vue frontend/tests/growthCenter.test.cjs
git commit -m "feat: integrate growth center subviews"
```

---

### Task 7: Regression And Visual Verification

**Files:**
- Modify only Task 1-6 files if verification exposes a defect.

- [ ] **Step 1: Run the backend growth slice**

```powershell
.\mvnw.cmd -Dtest=UserGrowthServiceTest,UserGrowthControllerTest,BadgeServiceTest,CheckinServiceTest,LeaderboardServiceTest test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run the frontend growth slice**

```powershell
node frontend/tests/growthCenter.test.cjs
node frontend/tests/achievementBadgeStyle.test.cjs
node frontend/tests/growthBadgeGallery.test.cjs
node frontend/tests/growthLevelRoadmap.test.cjs
node frontend/tests/profileTabTransition.test.cjs
```

Expected: every command exits 0.

- [ ] **Step 3: Check syntax and whitespace without a production build**

Run `git diff --check` and the SFC parser used above. Do not run `npm run build`.

- [ ] **Step 4: Verify desktop behavior on the existing dev server**

At `/profile` with an authenticated user, confirm all three growth subviews share the same left edge and width; rapid switching does not leave fixed height; badge filter combinations and counts are correct; Escape closes details and restores focus; completed/current/locked level nodes show correct descriptions; Lv10 has no next-level prompt.

- [ ] **Step 5: Verify mobile and reduced motion**

At 390x844, confirm one-column badges, bottom-sheet details, vertical route, and no horizontal overflow. With reduced motion enabled, subview motion, drawer entrance, and current-node pulse must be absent.

- [ ] **Step 6: Review exact scope**

Run `git status --short` and inspect each staged path. Confirm no database schema, experience-award rule, badge-award rule, or unrelated dirty file is included.

- [ ] **Step 7: Commit verification fixes only if necessary**

Stage exact changed files and commit `fix: polish growth gallery and level route`. If no changes were required, do not create an empty commit.
