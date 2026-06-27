# SkateHub Community Utility Modules Design

## Context

The PDF brief defines four first-version community utility modules for SkateHub: private messages, follows, comment replies, and points/levels. The existing project already has Spring Boot services for posts, comments, notifications, and growth experience, plus Vue pages for post detail and profile/notifications.

## Approved Scope

Build the first demonstrable version:

- One-to-one private messaging with conversations, text messages, conversation list, message history, unread counts, and read marking.
- User follows with follow/unfollow, follower/following lists, follow status, mutual-follow status, and follower/following counts.
- Comment replies by using the existing comment parent relation, adding reply target user information, reply listing, nested display, reply notifications, and reply/comment growth awards.
- Points and levels by reusing `UserGrowthService` and `tb_user_growth_log` as the single growth ledger.

Out of scope for this version: WebSocket realtime delivery, group chat, voice messages, red packets, paid messages, point marketplace, and virtual currency.

## Approach Options

### Option A: Incremental First Version

Reuse existing posts, comments, notifications, and growth services. Add only the missing follow/private-message tables and services, and extend comments with reply target fields. The frontend integrates into `PostDetailPage.vue` and `ProfilePage.vue`.

Trade-off: fastest and safest for graduation demo; not a full social platform.

### Option B: Full Social Layer

Introduce a larger social module with dedicated notification aggregation, richer message types, and separate user profile pages.

Trade-off: more complete, but too much scope for one implementation pass and higher regression risk.

### Option C: Backend Only

Implement APIs and schema only, leaving frontend demos to future work.

Trade-off: lower risk, but weaker for defense/demo because the user flow is not visible.

## Decision

Use Option A. It matches the PDF's first-version constraints, preserves the existing architecture, and gives a complete demo path without adding realtime infrastructure.

## Backend Design

Create entities and repositories:

- `PrivateConversation`: stores one conversation per user pair using `userAId` and `userBId` ordered by smaller/larger ID.
- `PrivateMessage`: stores text messages, sender/receiver, read status, message status, and timestamps.
- `UserFollow`: stores follower/followee relation with active/canceled status.

Extend `Comment`:

- Add `replyToUserId`, `likeCount`, `status`, and `updateTime`.
- Keep `parentId = 0` for root comments.
- Return nested comment view data from public post comments.

Create services:

- `PrivateMessageService`: create/get conversation, send message, list conversations, list messages, unread count, mark conversation read.
- `FollowService`: follow, unfollow, status, counts, follower/following lists.
- Extend `PostService`: save `replyToUserId`, validate parent comments, send reply notifications, award different growth actions for root comments and replies.

Growth points:

- Post create continues using existing growth ledger.
- Root comment uses `COMMENT_CREATE`.
- Reply uses `COMMENT_REPLY`.
- Follow uses `USER_FOLLOW`.
- Private messages do not grant points in the first version to avoid spam incentives.

## API Design

Authenticated endpoints:

- `POST /api/messages/conversations`
- `GET /api/messages/conversations`
- `GET /api/messages/conversations/{conversationId}`
- `POST /api/messages/conversations/{conversationId}/send`
- `POST /api/messages/conversations/{conversationId}/read`
- `GET /api/messages/unread-count`
- `POST /api/users/{userId}/follow`
- `DELETE /api/users/{userId}/follow`
- `GET /api/users/{userId}/followers`
- `GET /api/users/{userId}/following`
- `GET /api/users/{userId}/follow-status`
- `POST /api/comments/{commentId}/replies`
- `GET /api/comments/{commentId}/replies`
- `POST /api/comments/{commentId}/like`
- `DELETE /api/comments/{commentId}/like`

Public endpoints:

- `GET /api/public/users/{userId}/community-card`
- Existing `GET /api/public/posts/{id}/comments` returns root comments with limited nested replies.

## Frontend Design

Keep the UI quiet and utilitarian, matching the current SkateHub app:

- `PostDetailPage.vue`: show root comments with compact reply threads, reply-to controls, author level, and comment/reply submit states.
- `ProfilePage.vue`: add a community tab for private conversations, following, followers, unread count, and a compact message panel.
- Add API client methods in `frontend/src/api/index.js`.

No separate landing page is added. The main demo flows live where users naturally expect them.

## Data Migration

Update `database/init.sql` and add a dedicated migration file:

- Add `reply_to_user_id`, `like_count`, `status`, and `update_time` to `tb_comment`.
- Add `target_type` and `target_id` to `tb_message` if absent.
- Add `tb_private_conversation`.
- Add `tb_private_message`.
- Add `tb_user_follow`.
- Add indexes and unique constraints required for idempotency and list performance.

## Error Handling And Security

- All personal actions read the current user from `SecurityUtils.currentUser()`.
- Reject self-message and self-follow.
- Reject duplicate active follows.
- Validate message and comment text length.
- Ensure users can only read/send messages in conversations they belong to.
- Return profile data through explicit view maps, not raw sensitive fields.

## Testing

Backend unit tests:

- Private messages: one conversation per pair, self-message rejection, unread count, read marking, access control.
- Follows: follow, duplicate follow idempotency, unfollow, status, mutual status, self-follow rejection.
- Comments: nested replies, reply target user, reply notification, root/reply growth action split.

Frontend tests:

- API client methods point to the expected endpoints.
- Post detail logic groups replies under root comments.
- Profile/community logic exposes conversation and follow list state.

## Acceptance

The demo is accepted when a logged-in user can follow another user, send a private message, reply to a comment, receive notifications, and see growth/level changes remain consistent after refresh.
