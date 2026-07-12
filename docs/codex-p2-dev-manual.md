# SkateHub P2 Development Manual for Codex

## 1. Goal

Complete the long-term productization upgrades for SkateHub after P0 and P1 are stable:

1. Semantic search and hybrid RAG
2. Personalized training recommendation
3. Safety companion capability
4. Creator system
5. Product analytics and growth experiments

P2 should make SkateHub feel less like a graduation prototype and more like a sustainable vertical sports community product. Do not implement P2 before the core P0/P1 workflows are stable.

## 2. Project Snapshot

Tech stack:

- Backend: Spring Boot 3.3.5, Java 17, JPA, Security, Redis, MySQL
- Frontend: Vue CLI + Vue 3

Important existing backend areas:

- AI gateway: `src/main/java/com/skatehub/service/ai/AiGatewayService.java`
- AI coach: `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- AI coach RAG: `src/main/java/com/skatehub/service/ai/AiCoachRagService.java`
- Recommendation: `src/main/java/com/skatehub/service/RecommendationService.java`
- User growth: `src/main/java/com/skatehub/service/UserGrowthService.java`
- Training plans: `src/main/java/com/skatehub/service/TrainingPlanService.java`
- Tricks: `src/main/java/com/skatehub/service/TrickService.java`
- Activities: `src/main/java/com/skatehub/service/ActivityService.java`
- Videos: `src/main/java/com/skatehub/service/VideoService.java`
- Public profile: `src/main/java/com/skatehub/service/PublicUserProfileService.java`

Important existing frontend areas:

- AI coach page: `frontend/src/views/AiCoachPage.vue`
- Skate professional page: `frontend/src/views/SkateProfessionalPage.vue`
- Activity detail page: `frontend/src/views/ActivityDetailPage.vue`
- User profile page: `frontend/src/views/UserProfilePage.vue`
- Video watch page: `frontend/src/views/VideoWatchPage.vue`
- Profile page: `frontend/src/views/ProfilePage.vue`
- API layer: `frontend/src/api/index.js`

Relevant existing docs:

- P0 manual: `docs/codex-p0-dev-manual.md`
- P1 manual: `docs/codex-p1-dev-manual.md`
- AI Coach RAG design: `docs/superpowers/specs/2026-06-28-ai-coach-rag-design.md`
- Industry research: `docs/industry-best-practices-research.md`

## 3. Work Order

Implement P2 in this order:

1. Semantic search and hybrid RAG
2. Personalized training recommendation
3. Safety companion capability
4. Creator system
5. Product analytics and growth experiments

Reasoning:

- Semantic search improves AI Coach and discovery at the same time.
- Personalized recommendation depends on learning and growth data from P1.
- Safety companion depends on activity and place flows from P0.
- Creator system benefits from stable video, profile, and growth modules.
- Analytics should measure stable behavior instead of half-built flows.

## 4. P2-1 Semantic Search and Hybrid RAG

### Objective

Upgrade keyword-based retrieval into hybrid retrieval that combines:

- keyword match
- source type boost
- user context
- optional embedding similarity

This should improve AI Coach grounding and public content discovery without requiring a heavy external search platform in the first version.

### Current state

The project already has:

- AI gateway
- AI Coach chat
- RAG design document
- related content recommendation
- posts, videos, activities, tricks, training plans, equipment, and news as retrievable content

### Required changes

#### Backend

1. Create or refine a normalized knowledge chunk table.
2. Add a sync service that builds chunks from:
   - tricks
   - training plans
   - training tasks
   - videos
   - approved posts
   - equipment
   - activities
   - safety FAQ
3. Add optional embedding fields and interfaces:
   - do not require embeddings for the feature to work
   - keep keyword retrieval as fallback
4. Implement hybrid scoring:
   - title match
   - tag match
   - content match
   - source type boost
   - user level boost
   - embedding similarity when available
5. Expose references consistently to AI Coach.
6. Ensure private, rejected, inactive, or admin-only content is never indexed.

Likely backend touchpoints:

- `src/main/java/com/skatehub/service/ai/AiCoachRagService.java`
- `src/main/java/com/skatehub/service/ai/AiCoachRagContext.java`
- `src/main/java/com/skatehub/service/ai/AiCoachRagChunk.java`
- `src/main/java/com/skatehub/service/ai/AiGatewayService.java`
- `src/main/java/com/skatehub/config/AiProperties.java`
- repositories for tricks, training plans, videos, posts, activities, equipment, and news
- `database/init.sql` or a new migration script

#### Frontend

1. Keep AI Coach reference cards unchanged if P0 already implemented them.
2. Add reference confidence or source type labels only if backend provides them.
3. Improve empty states when no strong reference is found.
4. Optional: add a unified search surface if P1 search exists.

Likely frontend touchpoints:

- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/views/HomePage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- AI Coach answers use better references than pure keyword matching.
- Retrieval works without embeddings.
- Embedding failure does not break chat.
- Hidden or rejected content is never returned as a reference.

### Test ideas

- Hybrid retrieval returns a trick for explicit trick questions.
- Safety FAQ is boosted for injury/fall/protective gear questions.
- Rejected posts are excluded from chunks.
- Retrieval falls back to keyword mode when embeddings are missing.

## 5. P2-2 Personalized Training Recommendation

### Objective

Recommend next actions based on each user's level, training progress, check-ins, saved places, activity participation, and AI Coach questions.

### Current state

The project already has:

- user profile
- growth logs
- badges
- training plans
- training tasks
- trick check-ins
- AI Coach questions
- activity sign/check-in flow
- recommendations API

### Required changes

#### Backend

1. Build a user training profile summary:
   - current level
   - recent training tasks
   - completed tricks
   - stuck or repeated trick questions
   - active training plan
   - preferred city/place when available
2. Add recommendation categories:
   - next trick
   - next training task
   - recommended video
   - recommended activity
   - recommended place
   - safety reminder
3. Add scoring rules:
   - match difficulty to user level
   - prefer unfinished tasks
   - prefer recent active content
   - boost local activities
   - avoid repeatedly recommending completed items
4. Keep the first version rule-based.
5. Do not require machine learning infrastructure.

Likely backend touchpoints:

- `src/main/java/com/skatehub/service/RecommendationService.java`
- `src/main/java/com/skatehub/service/UserGrowthService.java`
- `src/main/java/com/skatehub/service/TrainingPlanService.java`
- `src/main/java/com/skatehub/service/TrickService.java`
- `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- `src/main/java/com/skatehub/controller/UserController.java`
- related growth/training repositories

#### Frontend

1. Add a "Next for You" panel.
2. Show grouped recommendations:
   - practice next
   - watch next
   - join next
   - review safety
3. Add clear action buttons:
   - start task
   - watch video
   - ask AI Coach
   - view activity
4. Avoid making the page feel like an ad feed.

Likely frontend touchpoints:

- `frontend/src/views/ProfilePage.vue`
- `frontend/src/views/SkateProfessionalPage.vue`
- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Logged-in users see recommendations tied to their current progress.
- Completed items are not repeatedly recommended.
- Anonymous users receive generic beginner recommendations.
- Recommendation failure does not break profile or skate pages.

### Test ideas

- Beginner user gets beginner tricks and videos.
- User who completed a task gets a next task.
- Local activity is boosted when city is known.
- Empty data returns generic defaults.

## 6. P2-3 Safety Companion Capability

### Objective

Add safety support around offline skate activities and places. This should help users prepare, attend, and return safely without turning the app into a medical or emergency service.

### Current state

The project already has:

- activity safety companion hints
- place safety tips
- activity detail page
- place detail page
- activity sign/check-in logic
- AI Coach safety answers

### Required changes

#### Backend

1. Add safety checklist templates:
   - protective gear
   - weather
   - lighting
   - traffic
   - beginner caution
   - hydration
2. Attach checklist items to:
   - activity detail
   - place detail
   - AI Coach safety replies
3. Add optional arrival confirmation status for signed activities.
4. Add optional post-activity return confirmation.
5. Add disclaimers for injury or emergency situations.
6. Do not store live location unless explicitly designed and approved later.

Likely backend touchpoints:

- `src/main/java/com/skatehub/service/ActivityService.java`
- `src/main/java/com/skatehub/service/PlaceReviewService.java`
- `src/main/java/com/skatehub/service/PublicContentService.java`
- `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- `src/main/java/com/skatehub/pojo/Activity.java`
- `src/main/java/com/skatehub/pojo/Place.java`
- activity sign/check-in entities

#### Frontend

1. Show safety checklist on activity detail.
2. Show safety checklist on place detail.
3. Add confirmation actions when supported:
   - I arrived
   - I checked in
   - I left safely
4. Keep copy direct and cautious.
5. Avoid suggesting medical diagnosis.

Likely frontend touchpoints:

- `frontend/src/views/ActivityDetailPage.vue`
- `frontend/src/views/PlaceDetailPage.vue`
- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Activity and place pages show practical safety preparation.
- Safety companion state does not block core activity participation.
- Serious injury content routes to cautious medical advice.
- No live tracking is introduced accidentally.

### Test ideas

- Night activities show lighting/return reminders.
- Beginner places show protective gear reminder.
- Injury questions trigger cautious AI answer rules.
- Arrival confirmation state persists if implemented.

## 7. P2-4 Creator System

### Objective

Support users who publish high-quality skate videos, tutorials, routes, and community posts. Creator identity should help content discovery and community trust.

### Current state

The project already has:

- video publishing
- post publishing
- public user profile
- follow/unfollow
- private messages
- badges and growth
- video watch page

### Required changes

#### Backend

1. Add creator profile summary:
   - published videos
   - tutorial posts
   - followers
   - total likes or collections
   - badges
2. Add content series support if practical:
   - series title
   - episode order
   - related tricks
   - difficulty
3. Add creator quality signals:
   - approved content count
   - report ratio
   - average engagement
   - recent activity
4. Add creator badges:
   - first tutorial
   - video contributor
   - trusted spot contributor
   - activity organizer
5. Keep creator features optional for ordinary users.

Likely backend touchpoints:

- `src/main/java/com/skatehub/service/PublicUserProfileService.java`
- `src/main/java/com/skatehub/service/VideoService.java`
- `src/main/java/com/skatehub/service/PostService.java`
- `src/main/java/com/skatehub/service/FollowService.java`
- `src/main/java/com/skatehub/service/BadgeService.java`
- `src/main/java/com/skatehub/pojo/Video.java`
- `src/main/java/com/skatehub/pojo/Post.java`
- `src/main/java/com/skatehub/pojo/User.java`

#### Frontend

1. Enrich public user profile with creator sections:
   - videos
   - tutorials
   - badges
   - follower count
   - contribution stats
2. Add creator cards on video and post pages.
3. Optional: add series display on video watch page.
4. Keep ordinary profiles clean if the user has no creator content.

Likely frontend touchpoints:

- `frontend/src/views/UserProfilePage.vue`
- `frontend/src/views/VideoWatchPage.vue`
- `frontend/src/views/PostDetailPage.vue`
- `frontend/src/views/ProfilePage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Creator profiles show useful contribution context.
- Ordinary users are not forced into creator UI.
- Video and tutorial content can link back to the creator.
- Creator badges are awarded idempotently.

### Test ideas

- User with videos shows creator section.
- User without creator content keeps simple profile.
- Follow count and creator stats match backend data.
- Creator badge is awarded once.

## 8. P2-5 Product Analytics and Growth Experiments

### Objective

Add lightweight analytics to understand usage and improve features without adding a complex analytics platform.

### Current state

The project already has:

- admin stats
- admin analytics endpoint
- audit logs
- growth logs
- activity and content data

### Required changes

#### Backend

1. Define product events:
   - view place
   - view activity
   - sign activity
   - check in activity
   - ask AI Coach
   - start training plan
   - complete training task
   - publish post
   - publish video
2. Store only necessary event fields:
   - event type
   - user id if logged in
   - target type
   - target id
   - timestamp
   - coarse source page
3. Add admin analytics summaries:
   - daily active users
   - activity conversion
   - AI Coach usage
   - training completion
   - content creation
4. Avoid logging sensitive chat content unless explicitly needed and sanitized.
5. Keep event tracking asynchronous or low overhead where possible.

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/AdminController.java`
- `src/main/java/com/skatehub/service/AdminService.java`
- `src/main/java/com/skatehub/service/AdminAuditLogService.java`
- `src/main/java/com/skatehub/service/UserGrowthService.java`
- new product analytics service/repository if needed

#### Frontend

1. Add lightweight event calls at important moments.
2. Do not block user actions if analytics fails.
3. Improve admin analytics display:
   - trend cards
   - conversion cards
   - top modules
4. Keep analytics UI operational, not decorative.

Likely frontend touchpoints:

- `frontend/src/views/AdminPage.vue`
- `frontend/src/views/ActivityDetailPage.vue`
- `frontend/src/views/PlaceDetailPage.vue`
- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/views/SkateProfessionalPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Admin can see basic product health and usage trends.
- Analytics failures do not affect user flows.
- Sensitive content is not unnecessarily stored.
- Events are consistently named and documented.

### Test ideas

- Event is recorded when a user signs up for activity.
- Analytics endpoint returns daily summaries.
- Anonymous events do not require user id.
- Failed analytics call is ignored by frontend.

## 9. Constraints

1. Do not introduce heavy infrastructure unless necessary.
2. Keep keyword search and basic recommendation as fallback.
3. Do not expose private or rejected content in retrieval, search, creator pages, or analytics.
4. Do not store live location data in P2 unless explicitly requested later.
5. Do not store raw sensitive AI chat content in analytics.
6. Do not let creator rankings encourage spam.
7. Keep all new long-term features compatible with P0/P1 flows.

## 10. Suggested Implementation Style

1. Start with data contracts and fallback behavior.
2. Keep every P2 feature usable without optional advanced capabilities.
3. Implement backend scoring rules plainly before adding AI or embeddings.
4. Use small DTOs for frontend-facing responses.
5. Add migrations separately and document them.
6. Prefer admin-visible diagnostics for background sync features.

## 11. Deliverables

When P2 is finished, the codebase should have:

- hybrid retrieval for AI Coach and search
- personalized training recommendations
- safer offline activity and place flows
- stronger creator identity and content discovery
- product analytics that help guide future iteration

## 12. Done Definition

P2 is done when:

- AI Coach and search can use better retrieval signals
- users receive useful next-step recommendations
- activity/place safety support is visible and cautious
- creators have meaningful public contribution surfaces
- admins can inspect product usage trends
- optional advanced features degrade gracefully

