# Public User Profile And Message Entry Design

## Background

SkateHub already has private messages, follows, comment replies, and a personal center. The current post detail page exposes a direct private-message action near the post author. That makes messaging too immediate: users can message from content without first seeing who the author is.

The desired behavior is closer to a Tieba-style user space: clicking another user's avatar or name opens that user's public profile. The public profile shows basic information and the user's posts. Private messaging is available from that public profile, not directly from the post detail page.

## Goals

- Add a public user profile page at `/users/:userId`.
- Show public, non-sensitive user information and that user's posts.
- Move the primary private-message entry to another user's profile page.
- Let post authors and comment authors link to their public profiles.
- Keep `/profile` as the current signed-in user's private personal center.
- Avoid exposing phone numbers, passwords, token versions, or private notification data.

## Non-Goals

- Do not redesign the full signed-in personal center.
- Do not add a full social feed, reply history, or activity history in this pass.
- Do not add real-time chat. Existing conversation creation and message APIs remain enough.
- Do not require users to follow each other before messaging.

## User Experience

### Public Profile Page

Route: `/users/:userId`

The page has a Tieba-style structure:

1. Header card
   - Avatar
   - Username
   - Level
   - Skate style
   - Gender
   - Bio
   - Post count
   - Follower count
   - Following count
   - Create time when available

2. Actions
   - If viewing another user while logged in:
     - Follow or unfollow
     - Private message
   - If viewing another user while logged out:
     - Buttons remain visible, but clicking asks the user to log in.
   - If viewing self:
     - Replace social actions with an "Enter personal center" action.

3. Content area
   - Default and only tab for this pass: user's posts.
   - Posts are sorted by create time descending.
   - Each post item shows title, category, create time, like count, comment count, and collect count.
   - Clicking a post opens `/community/post/:postId`.

### Post Detail Page

- Remove the direct private-message button from the author row.
- Make the post author's avatar/name navigate to `/users/:userId`.
- Keep direct follow only on the public profile page, so author actions stay consistent.

### Comments

- Make comment author names and avatars navigate to `/users/:userId`.
- Reply behavior stays unchanged.
- No private-message button appears inside comment rows.

### Conversation Jump

When the user clicks private message on another user's profile:

1. Frontend calls `POST /api/messages/conversations` with `{ targetUserId }`.
2. Backend returns or creates the conversation.
3. Frontend navigates to `/profile?tab=community&conversationId=<id>`.
4. `ProfilePage` opens the community/private-message panel and selects that conversation when the query parameter is present.

If the current API does not return the conversation id, update it to do so.

## Backend Design

Add public endpoints:

```text
GET /api/public/users/{userId}
GET /api/public/users/{userId}/posts?page=1&size=10
```

`GET /api/public/users/{userId}` returns:

```json
{
  "userId": 1,
  "username": "text1",
  "avatar": null,
  "gender": "male",
  "skateStyle": "street",
  "bio": "I like skateboarding.",
  "level": 3,
  "exp": 240,
  "postCount": 12,
  "followerCount": 8,
  "followingCount": 5,
  "createTime": "2026-06-02T16:41:08"
}
```

The response must not include:

- `password`
- `phone`
- `tokenVersion`
- admin-only fields
- notifications
- private messages

`GET /api/public/users/{userId}/posts` returns paged post summaries:

```json
{
  "total": 12,
  "list": [
    {
      "postId": 18,
      "title": "Skate practice story",
      "category": "experience",
      "createTime": "2026-06-02T16:41:08",
      "likeCount": 1,
      "collectCount": 0,
      "commentCount": 3
    }
  ]
}
```

Only normal visible posts should be returned.

## Frontend Design

Add API methods:

```js
publicUserProfile: (userId) => http.get(`/api/public/users/${userId}`),
publicUserPosts: (userId, params) => http.get(`/api/public/users/${userId}/posts`, { params })
```

Add route:

```js
{ path: '/users/:id', component: UserProfilePage }
```

Add page:

```text
frontend/src/views/UserProfilePage.vue
```

The page should follow the existing SkateHub visual language, but use a public-profile layout instead of the signed-in dashboard layout. It should feel like a user space: identity first, recent posts second, actions clear and restrained.

Update:

- `PostDetailPage.vue`
  - Remove direct private-message action.
  - Link author identity to `/users/:userId`.
  - Link comment identities to `/users/:userId`.

- `ProfilePage.vue`
  - Read `tab=community` and `conversationId` from route query.
  - Select the private-message panel and conversation if present.

## Error Handling

- Missing user: show a friendly empty state and keep a link back to the community page.
- Logged-out private message or follow: redirect to login or show the existing login prompt.
- Self profile: do not allow creating a conversation with self.
- Failed conversation creation: show the existing toast or alert error.
- Empty post list: show an empty state such as "No posts yet."

## Testing

Backend:

- Public user profile does not expose sensitive fields.
- Public user profile returns counts and growth level.
- Public user posts only returns that user's visible posts.
- Missing user returns the existing business error style.

Frontend:

- API method contract test for public profile and posts.
- User profile source contract test:
  - route exists,
  - private-message button is on user profile,
  - post detail does not contain direct private-message action,
  - author/comment links point to `/users/:id`.
- Query handling test for `/profile?tab=community&conversationId=...`.

## Acceptance Criteria

- A visitor can open another user's public profile from post author or comment author identity.
- Another user's profile shows basic public information and that user's posts.
- The private-message button is available on another user's profile.
- Clicking private message opens or creates a conversation and navigates to the personal center message panel.
- The post detail page no longer exposes a direct private-message button.
- Sensitive user fields are not returned by public profile APIs.
- Backend tests, frontend build, and relevant frontend contract tests pass.
