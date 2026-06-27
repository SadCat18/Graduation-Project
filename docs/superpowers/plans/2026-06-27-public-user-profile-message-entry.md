# Public User Profile Message Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Tieba-style public user profile page and move private messaging so it starts from another user's profile instead of directly from post detail.

**Architecture:** Add a backend public-profile service that returns only sanitized public user data and paged post summaries. Add a Vue route/page for `/users/:id`, link post/comment authors to it, and use the existing private-message creation API to jump into the current `/profile` private-message panel.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Vue 3, Vue Router, existing Axios `http` wrapper, Node-based frontend source contract tests.

---

## File Structure

- Create `src/main/java/com/skatehub/service/PublicUserProfileService.java`
  - Owns public user profile read models and post summary mapping.
  - Depends on `UserRepository`, `PostRepository`, `CommentRepository`, `UserFollowRepository`, and `UserGrowthService`.

- Modify `src/main/java/com/skatehub/controller/PublicController.java`
  - Adds `GET /api/public/users/{userId}` and `GET /api/public/users/{userId}/posts`.

- Modify `src/main/java/com/skatehub/dao/PostRepository.java`
  - Adds paged lookup for one user's posts.

- Create `src/test/java/com/skatehub/service/PublicUserProfileServiceTest.java`
  - Verifies sanitized profile fields, counts, paging, and missing-user behavior.

- Modify `frontend/src/api/index.js`
  - Adds `publicUserProfile` and `publicUserPosts`.

- Modify `frontend/src/router/index.js`
  - Adds lazy route `/users/:id`.

- Create `frontend/src/views/UserProfilePage.vue`
  - Renders public user header, follow/private-message actions, and that user's posts.

- Modify `frontend/src/views/PostDetailPage.vue`
  - Removes direct private-message action.
  - Links post author and comment authors to `/users/:id`.

- Modify `frontend/src/views/ProfilePage.vue`
  - Reads `tab=community` and `conversationId` query parameters.
  - Opens the private-message panel and selects the conversation after navigation from public profile.

- Create frontend tests:
  - Create `frontend/tests/publicUserProfileApi.test.cjs`.
  - Create `frontend/tests/publicUserProfilePage.test.cjs`.
  - Create `frontend/tests/postDetailUserLinks.test.cjs`.
  - Create `frontend/tests/profileConversationQuery.test.cjs`.

---

### Task 1: Backend Public Profile Service

**Files:**
- Modify: `src/main/java/com/skatehub/dao/PostRepository.java`
- Create: `src/main/java/com/skatehub/service/PublicUserProfileService.java`
- Test: `src/test/java/com/skatehub/service/PublicUserProfileServiceTest.java`

- [ ] **Step 1: Write the failing backend service tests**

Create `src/test/java/com/skatehub/service/PublicUserProfileServiceTest.java`:

```java
package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.BizException;
import com.skatehub.util.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicUserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
    private final UserGrowthService userGrowthService = mock(UserGrowthService.class);
    private final PublicUserProfileService service = new PublicUserProfileService(
            userRepository,
            postRepository,
            commentRepository,
            userFollowRepository,
            userGrowthService
    );

    @Test
    void publicProfileReturnsOnlySafeFieldsAndCounts() {
        User user = new User();
        user.setUserId(9L);
        user.setUsername("skater");
        user.setAvatar("/avatar.png");
        user.setGender("1");
        user.setSkateStyle("street");
        user.setBio("keep rolling");
        user.setLevel(4);
        user.setExp(360);
        user.setPhone("13800138000");
        user.setPassword("encoded");
        user.setTokenVersion(7);
        user.setCreateTime(LocalDateTime.of(2026, 6, 1, 12, 0));

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(postRepository.countByUserId(9L)).thenReturn(3L);
        when(userFollowRepository.countByFolloweeIdAndStatus(9L, "1")).thenReturn(8L);
        when(userFollowRepository.countByFollowerIdAndStatus(9L, "1")).thenReturn(5L);

        Map<String, Object> result = service.profile(9L);

        verify(userGrowthService).fillGrowthInfo(user);
        assertThat(result).containsEntry("userId", 9L);
        assertThat(result).containsEntry("username", "skater");
        assertThat(result).containsEntry("postCount", 3L);
        assertThat(result).containsEntry("followerCount", 8L);
        assertThat(result).containsEntry("followingCount", 5L);
        assertThat(result).containsEntry("level", 4);
        assertThat(result).containsEntry("exp", 360);
        assertThat(result).doesNotContainKeys("password", "phone", "tokenVersion");
    }

    @Test
    void publicProfileRejectsMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("user");
    }

    @Test
    void publicPostsReturnPagedSummariesForRequestedUser() {
        Post post = new Post();
        post.setPostId(18L);
        post.setUserId(9L);
        post.setTitle("Skate practice story");
        post.setCategory("experience");
        post.setCreateTime(LocalDateTime.of(2026, 6, 2, 16, 41));
        post.setLikeCount(2);
        post.setCollectCount(1);

        when(userRepository.existsById(9L)).thenReturn(true);
        when(postRepository.findByUserIdOrderByCreateTimeDesc(9L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(commentRepository.countByPostId(18L)).thenReturn(4L);

        PageResult<Map<String, Object>> result = service.posts(9L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0)).containsEntry("postId", 18L);
        assertThat(result.getList().get(0)).containsEntry("commentCount", 4L);
        assertThat(result.getList().get(0)).doesNotContainKeys("content", "images");
    }
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dtest=PublicUserProfileServiceTest" test
```

Expected: compilation fails because `PublicUserProfileService` and paged `findByUserIdOrderByCreateTimeDesc` do not exist.

- [ ] **Step 3: Add paged post lookup**

Modify `src/main/java/com/skatehub/dao/PostRepository.java` and add this overload next to the existing list lookup:

```java
Page<Post> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
```

- [ ] **Step 4: Implement `PublicUserProfileService`**

Create `src/main/java/com/skatehub/service/PublicUserProfileService.java`:

```java
package com.skatehub.service;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicUserProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserGrowthService userGrowthService;

    public Map<String, Object> profile(Long userId) {
        InputValidator.positiveId(userId, "userId");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("user not found"));
        userGrowthService.fillGrowthInfo(user);

        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("username", user.getUsername());
        map.put("avatar", user.getAvatar());
        map.put("gender", user.getGender());
        map.put("skateStyle", user.getSkateStyle());
        map.put("bio", user.getBio());
        map.put("level", user.getLevel());
        map.put("exp", user.getExp());
        map.put("postCount", postRepository.countByUserId(userId));
        map.put("followerCount", userFollowRepository.countByFolloweeIdAndStatus(userId, "1"));
        map.put("followingCount", userFollowRepository.countByFollowerIdAndStatus(userId, "1"));
        map.put("createTime", user.getCreateTime());
        return map;
    }

    public PageResult<Map<String, Object>> posts(Long userId, Integer page, Integer size) {
        InputValidator.positiveId(userId, "userId");
        if (!userRepository.existsById(userId)) {
            throw new BizException("user not found");
        }
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        Page<Post> postPage = postRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
        return PageResult.of(postPage.getTotalElements(), postPage.getContent().stream()
                .map(this::toPostSummary)
                .toList());
    }

    private Map<String, Object> toPostSummary(Post post) {
        Map<String, Object> map = new HashMap<>();
        map.put("postId", post.getPostId());
        map.put("title", post.getTitle());
        map.put("category", post.getCategory());
        map.put("createTime", post.getCreateTime());
        map.put("likeCount", post.getLikeCount() == null ? 0 : post.getLikeCount());
        map.put("collectCount", post.getCollectCount() == null ? 0 : post.getCollectCount());
        map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
        return map;
    }
}
```

- [ ] **Step 5: Run the service test to verify it passes**

Run:

```powershell
.\mvnw.cmd "-Dtest=PublicUserProfileServiceTest" test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit backend service**

Run:

```powershell
git add src/main/java/com/skatehub/dao/PostRepository.java src/main/java/com/skatehub/service/PublicUserProfileService.java src/test/java/com/skatehub/service/PublicUserProfileServiceTest.java
git commit -m "feat: add public user profile service"
```

---

### Task 2: Public User API Endpoints

**Files:**
- Modify: `src/main/java/com/skatehub/controller/PublicController.java`
- Test: `src/test/java/com/skatehub/service/PublicUserProfileServiceTest.java`

- [ ] **Step 1: Add controller dependency and endpoints**

Modify `src/main/java/com/skatehub/controller/PublicController.java`:

```java
import com.skatehub.service.PublicUserProfileService;
```

Add a field:

```java
private final PublicUserProfileService publicUserProfileService;
```

Add endpoints after the existing post comment endpoint:

```java
@GetMapping("/users/{userId}")
public ApiResponse<Map<String, Object>> publicUserProfile(
        @PathVariable("userId") @Positive(message = "userId must be positive") Long userId) {
    return ApiResponse.success(publicUserProfileService.profile(userId));
}

@GetMapping("/users/{userId}/posts")
public ApiResponse<PageResult<Map<String, Object>>> publicUserPosts(
        @PathVariable("userId") @Positive(message = "userId must be positive") Long userId,
        @RequestParam(required = false) @Min(1) @Max(10000) Integer page,
        @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
    return ApiResponse.success(publicUserProfileService.posts(userId, page, size));
}
```

- [ ] **Step 2: Run focused backend tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=PublicUserProfileServiceTest" test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit public endpoints**

Run:

```powershell
git add src/main/java/com/skatehub/controller/PublicController.java
git commit -m "feat: expose public user profile endpoints"
```

---

### Task 3: Frontend API, Route, And Public User Page

**Files:**
- Modify: `frontend/src/api/index.js`
- Modify: `frontend/src/router/index.js`
- Create: `frontend/src/views/UserProfilePage.vue`
- Create: `frontend/tests/publicUserProfileApi.test.cjs`
- Create: `frontend/tests/publicUserProfilePage.test.cjs`

- [ ] **Step 1: Write failing frontend API contract test**

Create `frontend/tests/publicUserProfileApi.test.cjs`:

```js
const fs = require('fs')
const path = require('path')

const apiSource = fs.readFileSync(path.join(__dirname, '../src/api/index.js'), 'utf8')

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

assertIncludes(apiSource, 'publicUserProfile: (userId) => http.get(`/api/public/users/${userId}`)', 'missing publicUserProfile api')
assertIncludes(apiSource, 'publicUserPosts: (userId, params) => http.get(`/api/public/users/${userId}/posts`, { params })', 'missing publicUserPosts api')

console.log('public user profile api contract passed')
```

- [ ] **Step 2: Write failing page/route contract test**

Create `frontend/tests/publicUserProfilePage.test.cjs`:

```js
const fs = require('fs')
const path = require('path')

function read(file) {
  return fs.readFileSync(path.join(__dirname, '..', file), 'utf8')
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

const router = read('src/router/index.js')
assertIncludes(router, 'UserProfilePage', 'router must lazy-load UserProfilePage')
assertIncludes(router, "path: '/users/:id'", 'router must expose /users/:id')

const page = read('src/views/UserProfilePage.vue')
assertIncludes(page, 'api.publicUserProfile', 'page must load public profile')
assertIncludes(page, 'api.publicUserPosts', 'page must load public posts')
assertIncludes(page, 'api.createConversation', 'page must start private conversation')
assertIncludes(page, "router.push(`/community/post/${post.postId}`)", 'page must navigate to post detail')
assertIncludes(page, "router.push({ path: '/profile', query: { tab: 'community', conversationId: String(conversationId) } })", 'page must jump to profile conversation')
assertIncludes(page, "router.push('/profile')", 'self profile action must go to personal center')

console.log('public user profile page contract passed')
```

- [ ] **Step 3: Run frontend contract tests to verify they fail**

Run:

```powershell
node frontend\tests\publicUserProfileApi.test.cjs
node frontend\tests\publicUserProfilePage.test.cjs
```

Expected: first test fails on missing API methods, second test fails because route/page do not exist.

- [ ] **Step 4: Add frontend API methods**

Modify `frontend/src/api/index.js` near public content APIs:

```js
publicUserProfile: (userId) => http.get(`/api/public/users/${userId}`),
publicUserPosts: (userId, params) => http.get(`/api/public/users/${userId}/posts`, { params }),
```

- [ ] **Step 5: Add route**

Modify `frontend/src/router/index.js`:

```js
const UserProfilePage = () => import(/* webpackChunkName: "view-user-profile" */ '../views/UserProfilePage.vue')
```

Add route:

```js
{ path: '/users/:id', component: UserProfilePage },
```

- [ ] **Step 6: Create public user page**

Create `frontend/src/views/UserProfilePage.vue` with this structure:

```vue
<template>
  <section class="user-profile-page">
    <div v-if="loading" class="panel-card">正在加载...</div>
    <div v-else-if="error" class="panel-card empty-state">
      <h2>用户不存在</h2>
      <p>{{ error }}</p>
      <button class="btn-soft" type="button" @click="router.push('/community')">返回社区</button>
    </div>
    <template v-else>
      <section class="profile-hero">
        <img class="profile-avatar" :src="profile.avatar || defaultAvatar" alt="" />
        <div class="profile-main">
          <p class="eyebrow">SkateHub user space</p>
          <h1>{{ profile.username || '匿名滑手' }}</h1>
          <p class="bio">{{ profile.bio || '这个滑手还没有填写简介。' }}</p>
          <div class="profile-meta">
            <span>Lv{{ profile.level || 1 }}</span>
            <span>{{ profile.skateStyle || '未设置风格' }}</span>
            <span>{{ profile.gender || '未设置性别' }}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button v-if="isSelf" class="btn-primary" type="button" @click="router.push('/profile')">进入个人中心</button>
          <template v-else>
            <button class="btn-soft" type="button" :disabled="actionLoading" @click="toggleFollow">
              {{ followStatus?.following ? '已关注' : '关注' }}
            </button>
            <button class="btn-primary" type="button" :disabled="actionLoading" @click="startConversation">私信</button>
          </template>
        </div>
      </section>

      <section class="stats-row">
        <div><strong>{{ profile.postCount || 0 }}</strong><span>帖子</span></div>
        <div><strong>{{ profile.followerCount || 0 }}</strong><span>粉丝</span></div>
        <div><strong>{{ profile.followingCount || 0 }}</strong><span>关注</span></div>
      </section>

      <section class="post-section">
        <div class="section-heading">
          <h2>TA 的帖子</h2>
          <span>{{ posts.length }} / {{ total }}</span>
        </div>
        <div v-if="postsLoading" class="panel-card">正在加载帖子...</div>
        <div v-else-if="!posts.length" class="panel-card empty-state">TA 还没有发布帖子。</div>
        <article v-for="post in posts" :key="post.postId" class="post-item" @click="router.push(`/community/post/${post.postId}`)">
          <div>
            <h3>{{ post.title }}</h3>
            <p>{{ post.category || '未分类' }} · {{ formatTime(post.createTime) }}</p>
          </div>
          <div class="post-metrics">
            <span>{{ post.likeCount || 0 }} 赞</span>
            <span>{{ post.commentCount || 0 }} 评</span>
            <span>{{ post.collectCount || 0 }} 藏</span>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { getToken, getUserId } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const defaultAvatar = 'https://api.dicebear.com/7.x/thumbs/svg?seed=skatehub'
const profile = ref({})
const posts = ref([])
const total = ref(0)
const followStatus = ref(null)
const loading = ref(true)
const postsLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const isLoggedIn = computed(() => Boolean(getToken()))
const currentUserId = computed(() => Number(getUserId() || 0))
const targetUserId = computed(() => Number(route.params.id))
const isSelf = computed(() => currentUserId.value && currentUserId.value === targetUserId.value)

function requireLogin() {
  if (isLoggedIn.value) return true
  router.push('/login')
  return false
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await api.publicUserProfile(targetUserId.value)
    if (isLoggedIn.value && !isSelf.value) {
      followStatus.value = await api.followStatus(targetUserId.value)
    } else {
      followStatus.value = null
    }
  } catch (e) {
    error.value = e?.message || '用户加载失败'
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  postsLoading.value = true
  try {
    const result = await api.publicUserPosts(targetUserId.value, { page: 1, size: 20 })
    posts.value = result?.list || []
    total.value = result?.total || posts.value.length
  } finally {
    postsLoading.value = false
  }
}

async function toggleFollow() {
  if (!requireLogin() || isSelf.value) return
  actionLoading.value = true
  try {
    followStatus.value = followStatus.value?.following
      ? await api.unfollowUser(targetUserId.value)
      : await api.followUser(targetUserId.value)
    await loadProfile()
  } finally {
    actionLoading.value = false
  }
}

async function startConversation() {
  if (!requireLogin() || isSelf.value) return
  actionLoading.value = true
  try {
    const conversation = await api.createConversation({ targetUserId: targetUserId.value })
    const conversationId = conversation?.conversationId
    router.push({ path: '/profile', query: { tab: 'community', conversationId: String(conversationId) } })
  } finally {
    actionLoading.value = false
  }
}

async function loadAll() {
  await loadProfile()
  if (!error.value) await loadPosts()
}

onMounted(loadAll)
watch(() => route.params.id, loadAll)
</script>

<style scoped>
.user-profile-page { max-width: 1100px; margin: 0 auto; padding: 28px 18px 56px; }
.profile-hero { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 22px; align-items: center; padding: 24px; border: 1px solid rgba(15,23,42,.1); border-radius: 8px; background: #fff; box-shadow: 0 18px 45px rgba(15,23,42,.08); }
.profile-avatar { width: 96px; height: 96px; border-radius: 50%; object-fit: cover; border: 3px solid #111827; }
.eyebrow { margin: 0 0 6px; color: #64748b; font-size: 12px; font-weight: 800; text-transform: uppercase; }
.profile-main h1 { margin: 0; font-size: 30px; }
.bio { color: #475569; line-height: 1.7; }
.profile-meta, .post-metrics { display: flex; flex-wrap: wrap; gap: 10px; color: #64748b; }
.profile-actions { display: flex; gap: 10px; }
.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0 22px; }
.stats-row div, .panel-card, .post-item { border: 1px solid rgba(15,23,42,.1); border-radius: 8px; background: #fff; }
.stats-row div { padding: 16px; }
.stats-row strong { display: block; font-size: 24px; }
.stats-row span { color: #64748b; }
.section-heading { display: flex; justify-content: space-between; align-items: center; }
.post-item { display: flex; justify-content: space-between; gap: 18px; padding: 18px; margin-bottom: 12px; cursor: pointer; transition: transform .18s ease, box-shadow .18s ease; }
.post-item:hover { transform: translateY(-2px); box-shadow: 0 14px 30px rgba(15,23,42,.08); }
.post-item h3 { margin: 0 0 8px; }
.post-item p { margin: 0; color: #64748b; }
.panel-card { padding: 22px; }
.empty-state { text-align: center; color: #64748b; }
.btn-primary, .btn-soft { border-radius: 8px; padding: 10px 14px; border: 1px solid #111827; cursor: pointer; }
.btn-primary { background: #111827; color: #fff; }
.btn-soft { background: #fff; color: #111827; }
@media (max-width: 720px) {
  .profile-hero { grid-template-columns: 1fr; text-align: center; }
  .profile-avatar { margin: 0 auto; }
  .profile-actions, .profile-meta, .post-metrics { justify-content: center; }
  .stats-row { grid-template-columns: 1fr; }
  .post-item { flex-direction: column; }
}
</style>
```

- [ ] **Step 7: Run frontend page tests**

Run:

```powershell
node frontend\tests\publicUserProfileApi.test.cjs
node frontend\tests\publicUserProfilePage.test.cjs
```

Expected: both tests print their success messages.

- [ ] **Step 8: Commit public profile page**

Run:

```powershell
git add frontend/src/api/index.js frontend/src/router/index.js frontend/src/views/UserProfilePage.vue frontend/tests/publicUserProfileApi.test.cjs frontend/tests/publicUserProfilePage.test.cjs
git commit -m "feat: add public user profile page"
```

---

### Task 4: Move Message Entry From Post Detail To User Profile

**Files:**
- Modify: `frontend/src/views/PostDetailPage.vue`
- Create: `frontend/tests/postDetailUserLinks.test.cjs`

- [ ] **Step 1: Write failing post-detail contract test**

Create `frontend/tests/postDetailUserLinks.test.cjs`:

```js
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.join(__dirname, '../src/views/PostDetailPage.vue'), 'utf8')

function assertIncludes(needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

function assertExcludes(needle, message) {
  if (source.includes(needle)) throw new Error(message)
}

assertIncludes('goUserProfile', 'post detail must define user profile navigation')
assertIncludes("router.push(`/users/${userId}`)", 'post detail must route to /users/:id')
assertIncludes('@click=\"goUserProfile(post.userId)\"', 'post author identity must link to public profile')
assertIncludes('@click=\"goUserProfile(comment.userId)\"', 'top-level comment author must link to public profile')
assertIncludes('@click=\"goUserProfile(reply.userId)\"', 'reply author must link to public profile')
assertExcludes('startAuthorConversation', 'post detail must not start private conversation directly')
assertExcludes('api.createConversation({ targetUserId: post.value.userId })', 'post detail must not call createConversation for author')

console.log('post detail user link contract passed')
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
node frontend\tests\postDetailUserLinks.test.cjs
```

Expected: fails because author/comment links are not wired and direct private-message action still exists.

- [ ] **Step 3: Modify script logic**

In `frontend/src/views/PostDetailPage.vue`, remove:

```js
async function startAuthorConversation() {
  if (!post.value?.userId) return
  authorActionLoading.value = true
  try {
    await api.createConversation({ targetUserId: post.value.userId })
    router.push('/profile')
  } catch (e) {
    showError(e, '私信会话创建失败')
  } finally {
    authorActionLoading.value = false
  }
}
```

Add:

```js
function goUserProfile(userId) {
  if (!userId) return
  router.push(`/users/${userId}`)
}
```

The file already uses `router`, so keep that existing instance and add only the new `goUserProfile` function.

- [ ] **Step 4: Modify post author template**

Replace the author line action area with a clickable author identity. Keep no private-message button in this page:

```vue
<div class="author-line">
  <button class="author-link" type="button" @click="goUserProfile(post.userId)">
    作者：{{ post.authorName }} · Lv{{ post.authorLevel || 1 }}
  </button>
  <p class="muted">{{ post.createTime?.replace('T', ' ') }}</p>
</div>
```

Remove the old `私信` button:

```vue
<button class="btn-soft" :disabled="authorActionLoading" @click="startAuthorConversation">私信</button>
```

Remove the direct follow button from post detail if it sits next to the private-message button. Follow remains available on `/users/:id`.

- [ ] **Step 5: Modify comment and reply author templates**

For each top-level comment author area, use:

```vue
<button class="comment-author" type="button" @click="goUserProfile(comment.userId)">
  {{ comment.username || '匿名滑手' }}
</button>
```

For each reply author area, use:

```vue
<button class="comment-author" type="button" @click="goUserProfile(reply.userId)">
  {{ reply.username || '匿名滑手' }}
</button>
```

Keep reply buttons and like buttons unchanged.

- [ ] **Step 6: Add minimal styles**

Add or adjust scoped CSS in `PostDetailPage.vue`:

```css
.author-link,
.comment-author {
  border: 0;
  padding: 0;
  background: transparent;
  color: #111827;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.author-link:hover,
.comment-author:hover {
  text-decoration: underline;
}
```

- [ ] **Step 7: Run post-detail test**

Run:

```powershell
node frontend\tests\postDetailUserLinks.test.cjs
```

Expected: `post detail user link contract passed`.

- [ ] **Step 8: Commit post-detail entry move**

Run:

```powershell
git add frontend/src/views/PostDetailPage.vue frontend/tests/postDetailUserLinks.test.cjs
git commit -m "feat: move private message entry to user profile"
```

---

### Task 5: Profile Query Handoff For Conversations

**Files:**
- Modify: `frontend/src/views/ProfilePage.vue`
- Create: `frontend/tests/profileConversationQuery.test.cjs`

- [ ] **Step 1: Write failing query contract test**

Create `frontend/tests/profileConversationQuery.test.cjs`:

```js
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.join(__dirname, '../src/views/ProfilePage.vue'), 'utf8')

function assertIncludes(needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

assertIncludes('useRoute', 'ProfilePage must import useRoute')
assertIncludes('const route = useRoute()', 'ProfilePage must create route instance')
assertIncludes("route.query.tab === 'community'", 'ProfilePage must read tab=community')
assertIncludes('route.query.conversationId', 'ProfilePage must read conversationId query')
assertIncludes('selectConversationById', 'ProfilePage must expose conversation selection by id')

console.log('profile conversation query contract passed')
```

- [ ] **Step 2: Run query test to verify it fails**

Run:

```powershell
node frontend\tests\profileConversationQuery.test.cjs
```

Expected: fails because query handling is not present.

- [ ] **Step 3: Add route query setup**

In `frontend/src/views/ProfilePage.vue`, update the router import:

```js
import { useRoute, useRouter } from 'vue-router'
```

Add setup state:

```js
const route = useRoute()
```

The file already imports `useRouter`; add `useRoute` to that same import.

- [ ] **Step 4: Add conversation selection helper**

Add this helper near `loadConversationMessages`:

```js
async function selectConversationById(conversationId) {
  const normalizedId = Number(conversationId)
  if (!normalizedId) return
  const target = privateConversations.value.find(item => Number(item.conversationId) === normalizedId)
  if (target) {
    await loadConversationMessages(target.conversationId)
  }
}
```

- [ ] **Step 5: Open the community/private-message tab from query**

After conversations load, add:

```js
async function applyConversationQuery() {
  if (route.query.tab === 'community') {
    profileTab.value = 'community'
  }
  if (route.query.conversationId) {
    await selectConversationById(route.query.conversationId)
  }
}
```

Call it inside `loadCommunityUtilities()` after `privateConversations.value`, `followersList.value`, and `followingList.value` are assigned, and before the fallback that selects the first conversation:

```js
await applyConversationQuery()
if (!selectedConversationId.value && privateConversations.value.length) {
  selectedConversationId.value = privateConversations.value[0].conversationId
  await loadConversationMessages(selectedConversationId.value)
}
```
- [ ] **Step 6: Run profile query test**

Run:

```powershell
node frontend\tests\profileConversationQuery.test.cjs
```

Expected: `profile conversation query contract passed`.

- [ ] **Step 7: Commit query handoff**

Run:

```powershell
git add frontend/src/views/ProfilePage.vue frontend/tests/profileConversationQuery.test.cjs
git commit -m "feat: open profile conversation from query"
```

---

### Task 6: Full Verification And Cleanup

**Files:**
- All files touched in Tasks 1-5

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=PublicUserProfileServiceTest,PrivateMessageServiceTest,FollowServiceTest,PostServiceReplyTest" test
```

Expected: `BUILD SUCCESS`, with zero failures and zero errors.

- [ ] **Step 2: Run full backend test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 187` or more, `Failures: 0`, `Errors: 0`, `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend contract tests**

Run:

```powershell
node frontend\tests\publicUserProfileApi.test.cjs
node frontend\tests\publicUserProfilePage.test.cjs
node frontend\tests\postDetailUserLinks.test.cjs
node frontend\tests\profileConversationQuery.test.cjs
node frontend\tests\communityUtilityApi.test.cjs
node frontend\tests\postRepliesLogic.test.cjs
```

Expected: every command exits 0 and prints its success message where defined.

- [ ] **Step 4: Run frontend production build**

Run:

```powershell
npm run build
```

Working directory:

```text
D:\College\Graduation Project\SE1\frontend
```

Expected: Vue CLI prints `Compiled successfully`.

- [ ] **Step 5: Inspect git status**

Run:

```powershell
git status --short --branch
```

Expected: working tree is clean except ignored runtime artifacts. If files remain, inspect them with:

```powershell
git diff --stat
git status --short
```

- [ ] **Step 6: Confirm no runtime artifacts are staged**

Run:

```powershell
git diff --cached --name-only | Select-String -Pattern '(^frontend/dist/|\\.log$|^\\.superpowers/|^tmp/|^output/|frontend/.playwright-cli/)'
```

Expected: no output. Do not commit `frontend/dist`, `*.log`, `.superpowers`, `tmp`, `output`, or Playwright capture files.

---

## Self-Review

- Spec coverage:
  - Public user page route `/users/:userId`: Task 3.
  - Public sanitized backend profile: Task 1 and Task 2.
  - User posts list: Task 1, Task 2, Task 3.
  - Private message entry only from public profile: Task 3 and Task 4.
  - Author/comment profile links: Task 4.
  - Profile query handoff to conversation panel: Task 5.
  - Tests and verification: Task 6.

- Placeholder scan:
  - This plan avoids placeholder terms and includes exact files, commands, expected outputs, and code snippets for each implementation step.

- Type consistency:
  - Backend service method names are `profile` and `posts`.
  - Frontend API methods are `publicUserProfile` and `publicUserPosts`.
  - Route path is `/users/:id` in Vue and `/users/:userId` in backend.
  - Conversation handoff query keys are `tab=community` and `conversationId=<id>`.
