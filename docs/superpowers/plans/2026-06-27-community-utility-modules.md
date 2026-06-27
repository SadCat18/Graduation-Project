# Community Utility Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SkateHub's first-version private messaging, follows, comment replies, and growth-linked community utility flows.

**Architecture:** Add small Spring Boot domain modules for private messages and follows, extend existing comments for replies, and reuse existing notification and growth services. Vue integration stays in `PostDetailPage.vue`, `ProfilePage.vue`, and the shared API client.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, JUnit 5/Mockito, Vue 3, CommonJS frontend logic tests.

---

## File Structure

- Create `src/main/java/com/skatehub/pojo/PrivateConversation.java`: JPA entity for one-to-one conversations.
- Create `src/main/java/com/skatehub/pojo/PrivateMessage.java`: JPA entity for private message rows.
- Create `src/main/java/com/skatehub/pojo/UserFollow.java`: JPA entity for follow rows.
- Create `src/main/java/com/skatehub/dao/PrivateConversationRepository.java`: conversation lookup and list queries.
- Create `src/main/java/com/skatehub/dao/PrivateMessageRepository.java`: message list, unread count, and bulk read updates.
- Create `src/main/java/com/skatehub/dao/UserFollowRepository.java`: active follow lookup, lists, counts, and mutual status.
- Create `src/main/java/com/skatehub/pojo/message/ConversationCreateRequest.java`: target user request.
- Create `src/main/java/com/skatehub/pojo/message/MessageSendRequest.java`: text send request.
- Create `src/main/java/com/skatehub/service/PrivateMessageService.java`: private message business logic.
- Create `src/main/java/com/skatehub/service/FollowService.java`: follow business logic.
- Create `src/main/java/com/skatehub/controller/PrivateMessageController.java`: `/api/messages` endpoints.
- Create `src/main/java/com/skatehub/controller/FollowController.java`: `/api/users/{userId}/follow*` endpoints.
- Modify `src/main/java/com/skatehub/pojo/Comment.java`: add reply target, status, like count, update time.
- Modify `src/main/java/com/skatehub/pojo/comment/CommentCreateRequest.java`: add reply target user id.
- Modify `src/main/java/com/skatehub/dao/CommentRepository.java`: add root/reply queries.
- Modify `src/main/java/com/skatehub/service/PostService.java`: nested comment views, reply validation, reply notifications, growth actions.
- Modify `src/main/java/com/skatehub/controller/PostController.java`: add reply/like routes.
- Modify `src/main/java/com/skatehub/controller/PublicController.java`: add user community card endpoint.
- Modify `src/main/java/com/skatehub/service/UserService.java`: user community card helper.
- Modify `database/init.sql`: schema additions.
- Create `database/add-community-utility-modules.sql`: repeatable migration for existing databases.
- Create `src/test/java/com/skatehub/service/PrivateMessageServiceTest.java`.
- Create `src/test/java/com/skatehub/service/FollowServiceTest.java`.
- Create `src/test/java/com/skatehub/service/PostServiceReplyTest.java`.
- Modify `frontend/src/api/index.js`: API client methods.
- Modify `frontend/src/views/PostDetailPage.vue`: nested comments and reply controls.
- Modify `frontend/src/views/ProfilePage.vue`: community tab for follows/messages.
- Create `frontend/tests/communityUtilityApi.test.cjs`.
- Create `frontend/tests/postRepliesLogic.test.cjs`.

---

### Task 1: Backend Private Messaging

**Files:**
- Create: `src/main/java/com/skatehub/pojo/PrivateConversation.java`
- Create: `src/main/java/com/skatehub/pojo/PrivateMessage.java`
- Create: `src/main/java/com/skatehub/dao/PrivateConversationRepository.java`
- Create: `src/main/java/com/skatehub/dao/PrivateMessageRepository.java`
- Create: `src/main/java/com/skatehub/pojo/message/ConversationCreateRequest.java`
- Create: `src/main/java/com/skatehub/pojo/message/MessageSendRequest.java`
- Create: `src/main/java/com/skatehub/service/PrivateMessageService.java`
- Create: `src/main/java/com/skatehub/controller/PrivateMessageController.java`
- Test: `src/test/java/com/skatehub/service/PrivateMessageServiceTest.java`

- [ ] **Step 1: Write failing tests**

Cover: ordered user pair creates one conversation, self-message rejected, send updates last message, unread count counts receiver unread rows, mark read only affects current user's received messages, and access outside a conversation is rejected.

- [ ] **Step 2: Run red test**

Run: `.\mvnw.cmd -Dtest=PrivateMessageServiceTest test`

Expected: compilation fails because classes do not exist.

- [ ] **Step 3: Implement minimal private messaging module**

Follow the file responsibilities above. Return maps with `conversationId`, `otherUserId`, `otherUsername`, `otherAvatar`, `lastMessageContent`, `lastMessageTime`, `unreadCount`, `messageId`, `senderId`, `receiverId`, `content`, `readStatus`, and `createTime`.

- [ ] **Step 4: Run green test**

Run: `.\mvnw.cmd -Dtest=PrivateMessageServiceTest test`

Expected: tests pass.

---

### Task 2: Backend Follows

**Files:**
- Create: `src/main/java/com/skatehub/pojo/UserFollow.java`
- Create: `src/main/java/com/skatehub/dao/UserFollowRepository.java`
- Create: `src/main/java/com/skatehub/service/FollowService.java`
- Create: `src/main/java/com/skatehub/controller/FollowController.java`
- Test: `src/test/java/com/skatehub/service/FollowServiceTest.java`

- [ ] **Step 1: Write failing tests**

Cover: self-follow rejected, first follow creates active relation and awards `USER_FOLLOW`, duplicate active follow is idempotent, canceled follow can reactivate, unfollow changes status, status includes `following`, `mutual`, `followerCount`, and `followingCount`.

- [ ] **Step 2: Run red test**

Run: `.\mvnw.cmd -Dtest=FollowServiceTest test`

Expected: compilation fails because classes do not exist.

- [ ] **Step 3: Implement minimal follow module**

Use status `1` for active and `0` for canceled. Return explicit user cards from list methods without phone/password fields.

- [ ] **Step 4: Run green test**

Run: `.\mvnw.cmd -Dtest=FollowServiceTest test`

Expected: tests pass.

---

### Task 3: Comment Replies And Growth

**Files:**
- Modify: `src/main/java/com/skatehub/pojo/Comment.java`
- Modify: `src/main/java/com/skatehub/pojo/comment/CommentCreateRequest.java`
- Modify: `src/main/java/com/skatehub/dao/CommentRepository.java`
- Modify: `src/main/java/com/skatehub/service/PostService.java`
- Modify: `src/main/java/com/skatehub/controller/PostController.java`
- Modify: `src/main/java/com/skatehub/controller/PublicController.java`
- Modify: `src/main/java/com/skatehub/service/UserService.java`
- Test: `src/test/java/com/skatehub/service/PostServiceReplyTest.java`

- [ ] **Step 1: Write failing tests**

Cover: root comments return nested replies, reply stores parent and reply target user, replying notifies the parent comment author, root comments award `COMMENT_CREATE`, replies award `COMMENT_REPLY`, and comment like toggles an interaction with target type `COMMENT`.

- [ ] **Step 2: Run red test**

Run: `.\mvnw.cmd -Dtest=PostServiceReplyTest test`

Expected: fail because behavior is not implemented.

- [ ] **Step 3: Implement comment reply behavior**

Keep root `parentId` as `0`. Validate parent belongs to the same post. Use soft status field for future deletion compatibility. Preserve existing public comment endpoint but return root comments with `replies`.

- [ ] **Step 4: Run green test**

Run: `.\mvnw.cmd -Dtest=PostServiceReplyTest test`

Expected: tests pass.

---

### Task 4: Database Schema

**Files:**
- Modify: `database/init.sql`
- Create: `database/add-community-utility-modules.sql`
- Test: `src/test/java/com/skatehub/growth/GrowthSchemaContractTest.java`

- [ ] **Step 1: Write/extend failing schema contract**

Assert the migration contains `tb_private_conversation`, `tb_private_message`, `tb_user_follow`, `reply_to_user_id`, and unique indexes for conversation and follows.

- [ ] **Step 2: Run red test**

Run: `.\mvnw.cmd -Dtest=GrowthSchemaContractTest test`

Expected: fails because schema additions are absent.

- [ ] **Step 3: Add schema SQL**

Add idempotent `CREATE TABLE IF NOT EXISTS` statements and `ALTER TABLE ... ADD COLUMN` guarded by MySQL-compatible migration statements in the dedicated migration.

- [ ] **Step 4: Run green test**

Run: `.\mvnw.cmd -Dtest=GrowthSchemaContractTest test`

Expected: tests pass.

---

### Task 5: Frontend API And Reply UI

**Files:**
- Modify: `frontend/src/api/index.js`
- Modify: `frontend/src/views/PostDetailPage.vue`
- Create: `frontend/tests/communityUtilityApi.test.cjs`
- Create: `frontend/tests/postRepliesLogic.test.cjs`

- [ ] **Step 1: Write failing frontend tests**

Cover API client endpoint strings and a small exported/grouped helper that keeps replies under their root comments.

- [ ] **Step 2: Run red tests**

Run: `cd frontend; npm test -- communityUtilityApi.test.cjs postRepliesLogic.test.cjs`

Expected: fails because methods/helper do not exist.

- [ ] **Step 3: Implement API methods and reply UI**

Add methods for private messages, follows, reply creation, reply listing, comment like/unlike, and public user card. Update `PostDetailPage.vue` with reply buttons, nested reply rows, and reply input state.

- [ ] **Step 4: Run green frontend tests**

Run: `cd frontend; npm test -- communityUtilityApi.test.cjs postRepliesLogic.test.cjs`

Expected: tests pass.

---

### Task 6: Frontend Profile Community Tab

**Files:**
- Modify: `frontend/src/views/ProfilePage.vue`
- Modify: `frontend/src/api/index.js`
- Test: `frontend/tests/communityUtilityApi.test.cjs`

- [ ] **Step 1: Write failing test**

Extend API test coverage for conversation list, send message, unread count, followers, following, follow status, follow, and unfollow.

- [ ] **Step 2: Run red test**

Run: `cd frontend; npm test -- communityUtilityApi.test.cjs`

Expected: fails for missing methods if Task 5 did not already add all of them.

- [ ] **Step 3: Implement community tab**

Add a `community` profile tab with private message conversation list, selected conversation messages, send box, follower list, following list, and unread count. Keep controls compact and consistent with existing card/list patterns.

- [ ] **Step 4: Run green test**

Run: `cd frontend; npm test -- communityUtilityApi.test.cjs`

Expected: tests pass.

---

### Task 7: Full Verification

**Files:**
- All files changed above.

- [ ] **Step 1: Run targeted backend tests**

Run: `.\mvnw.cmd -Dtest=PrivateMessageServiceTest,FollowServiceTest,PostServiceReplyTest,GrowthSchemaContractTest test`

Expected: build succeeds and tests pass.

- [ ] **Step 2: Run targeted frontend tests**

Run: `cd frontend; npm test -- communityUtilityApi.test.cjs postRepliesLogic.test.cjs`

Expected: tests pass.

- [ ] **Step 3: Run broader smoke tests**

Run: `.\mvnw.cmd test`

Expected: backend test suite passes, or report unrelated pre-existing failures with exact failing tests.

- [ ] **Step 4: Build frontend**

Run: `cd frontend; npm run build`

Expected: build succeeds.
