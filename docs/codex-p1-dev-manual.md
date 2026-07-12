# SkateHub P1 Development Manual for Codex

## 1. Goal

Complete the second-stage upgrade blocks for SkateHub after P0 is stable:

1. Community governance
2. Growth system deepening
3. Learning path enhancement
4. Search and recommendation foundation
5. Observability and operational stability

P1 should strengthen product completeness and long-term maintainability. Keep the scope practical: improve existing modules first, then add small new surfaces only when they close a clear workflow gap.

## 2. Project Snapshot

Tech stack:

- Backend: Spring Boot 3.3.5, Java 17, JPA, Security, Redis, MySQL
- Frontend: Vue CLI + Vue 3

Important existing backend areas:

- Community posts: `src/main/java/com/skatehub/controller/PostController.java`
- Admin moderation: `src/main/java/com/skatehub/controller/AdminController.java`
- Reports: `src/main/java/com/skatehub/service/ReportService.java`
- Moderation rules: `src/main/java/com/skatehub/service/ModerationRuleService.java`
- AI moderation assist: `src/main/java/com/skatehub/service/ai/AiModerationAssistantService.java`
- Growth: `src/main/java/com/skatehub/service/UserGrowthService.java`
- Badges: `src/main/java/com/skatehub/service/BadgeService.java`
- Badge award listener: `src/main/java/com/skatehub/service/BadgeAwardListener.java`
- Leaderboard: `src/main/java/com/skatehub/service/LeaderboardService.java`
- Tricks: `src/main/java/com/skatehub/service/TrickService.java`
- Training plans: `src/main/java/com/skatehub/service/TrainingPlanService.java`

Important existing frontend areas:

- Admin page: `frontend/src/views/AdminPage.vue`
- Community page: `frontend/src/views/CommunityPage.vue`
- Post detail: `frontend/src/views/PostDetailPage.vue`
- Post publish: `frontend/src/views/PostPublishPage.vue`
- Profile page: `frontend/src/views/ProfilePage.vue`
- Skate professional page: `frontend/src/views/SkateProfessionalPage.vue`
- Growth components: `frontend/src/components/growth`
- Frontend API layer: `frontend/src/api/index.js`

## 3. Work Order

Implement P1 in this order:

1. Community governance
2. Growth system deepening
3. Learning path enhancement
4. Search and recommendation foundation
5. Observability and stability

Community governance should come first because it protects content quality as the rest of the system grows.

## 4. P1-1 Community Governance

### Objective

Upgrade the community from basic posting and reporting into a manageable content ecosystem with clearer review states, handling reasons, and lightweight pre-publish safety.

### Current state

The project already has:

- posts
- comments
- reports
- admin post/comment management
- admin report handling
- batch delete/top operations
- AI moderation suggestion service
- admin audit log service

### Required changes

#### Backend

1. Normalize moderation status and handling reasons.
2. Add or refine report handling templates:
   - spam
   - harassment
   - unsafe offline activity
   - duplicate content
   - irrelevant content
   - false report
3. Add review queue filters:
   - pending
   - handled
   - rejected
   - false report
   - target type
   - time range
4. Ensure admin operations write audit logs consistently.
5. Use AI moderation suggestions as advisory output only; do not auto-delete without explicit admin action.
6. Add pre-publish content checks for posts and comments:
   - sensitive keywords
   - excessive links
   - repeated text
   - too-short or empty content

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/AdminController.java`
- `src/main/java/com/skatehub/service/ReportService.java`
- `src/main/java/com/skatehub/service/ModerationRuleService.java`
- `src/main/java/com/skatehub/service/AdminAuditLogService.java`
- `src/main/java/com/skatehub/service/ai/AiModerationAssistantService.java`
- `src/main/java/com/skatehub/pojo/ReportRecord.java`
- `src/main/java/com/skatehub/pojo/report/ReportHandleRequest.java`
- `src/main/java/com/skatehub/pojo/report/ReportCreateRequest.java`
- `src/main/java/com/skatehub/pojo/Post.java`
- `src/main/java/com/skatehub/pojo/Comment.java`

#### Frontend

1. Improve the admin report queue:
   - filters
   - status chips
   - target preview
   - handler and handled time
   - reason template selector
2. Add clearer post/comment admin actions:
   - delete
   - batch delete
   - top / untop
   - view context
3. Add pre-publish warnings on post/comment forms.
4. Keep the user able to submit after warnings unless the backend rejects the content.

Likely frontend touchpoints:

- `frontend/src/views/AdminPage.vue`
- `frontend/src/views/PostPublishPage.vue`
- `frontend/src/views/PostDetailPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Admin can filter and handle reports with a structured reason.
- Handling a report records an audit log.
- Post/comment publishing surfaces warnings before obvious risky content.
- AI moderation suggestions are visible as assistance, not as automatic enforcement.

### Test ideas

- Report queue filters return the correct states.
- Report handling stores reason, handler, and time.
- Admin action creates an audit log.
- Pre-publish warning does not block allowed content.

## 5. P1-2 Growth System Deepening

### Objective

Make growth feel tied to real skate behavior, not only login or generic points. Users should understand what they have achieved and what to do next.

### Current state

The project already has:

- user growth logs
- badges
- badge awarding listener
- daily check-in
- leaderboard
- level roadmap
- growth center UI components

### Required changes

#### Backend

1. Define a clearer growth action map:
   - daily check-in
   - trick check-in
   - training task completion
   - activity sign-up
   - activity check-in
   - place review
   - post publish
   - useful comment
2. Add daily caps where needed to prevent farming.
3. Add milestone badges:
   - first trick check-in
   - 7-day practice streak
   - first activity check-in
   - first place review
   - beginner learning path completed
4. Keep badge awarding idempotent.
5. Make leaderboard dimensions clearer:
   - total growth
   - weekly practice
   - activity participation
   - community contribution

Likely backend touchpoints:

- `src/main/java/com/skatehub/service/UserGrowthService.java`
- `src/main/java/com/skatehub/service/BadgeService.java`
- `src/main/java/com/skatehub/service/BadgeAwardListener.java`
- `src/main/java/com/skatehub/service/LeaderboardService.java`
- `src/main/java/com/skatehub/config/GrowthTimeConfig.java`
- `src/main/java/com/skatehub/pojo/UserGrowthLog.java`
- `src/main/java/com/skatehub/pojo/Badge.java`
- `src/main/java/com/skatehub/pojo/UserBadge.java`
- `src/main/java/com/skatehub/pojo/growth/LeaderboardResponse.java`

#### Frontend

1. Make the growth center show:
   - current level
   - next level requirement
   - recently earned points
   - next recommended action
2. Expand badge gallery with locked/unlocked states.
3. Add milestone cards to profile.
4. Add leaderboard tabs for dimensions if supported by backend.
5. Keep the growth display compact and useful, not decorative.

Likely frontend touchpoints:

- `frontend/src/views/ProfilePage.vue`
- `frontend/src/components/growth/GrowthCenterPanel.vue`
- `frontend/src/components/growth/BadgeGalleryPanel.vue`
- `frontend/src/components/growth/BadgeDetailDrawer.vue`
- `frontend/src/components/growth/LevelRoadmapPanel.vue`
- `frontend/src/utils/growthCenter.js`
- `frontend/src/api/index.js`

### Acceptance criteria

- Users can see what action will help them progress next.
- Badges have clear locked/unlocked states.
- Growth points are not easily farmed by repeated same-day actions.
- Leaderboard dimensions are understandable.

### Test ideas

- Same action does not exceed daily growth cap.
- Badge awarding is idempotent.
- Level roadmap displays next requirement correctly.
- Leaderboard tabs map to backend dimensions correctly.

## 6. P1-3 Learning Path Enhancement

### Objective

Turn tricks, videos, and training plans into a clearer learning path for beginners and intermediate skaters.

### Current state

The project already has:

- trick list and detail APIs
- trick check-ins
- training plans
- training tasks
- current user training plan
- training task completion
- videos
- AI coach recommendations

### Required changes

#### Backend

1. Add or expose learning path metadata:
   - stage
   - difficulty
   - prerequisite tricks
   - recommended next tricks
   - linked videos
   - linked training tasks
2. Add completion state per user:
   - not started
   - practicing
   - completed
3. Connect training plan tasks with trick check-ins where practical.
4. Provide a beginner path endpoint if existing APIs cannot assemble it cleanly.
5. Keep AI Coach able to reference path items later.

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/UserSkateController.java`
- `src/main/java/com/skatehub/controller/SkateController.java`
- `src/main/java/com/skatehub/service/TrickService.java`
- `src/main/java/com/skatehub/service/TrainingPlanService.java`
- `src/main/java/com/skatehub/service/VideoService.java`
- `src/main/java/com/skatehub/pojo/skate/SkateTrick.java`
- `src/main/java/com/skatehub/pojo/skate/TrainingPlan.java`
- `src/main/java/com/skatehub/pojo/skate/TrainingTask.java`
- `src/main/java/com/skatehub/pojo/skate/TrickCheckin.java`

#### Frontend

1. Add a clear learning path section on the skate professional page.
2. Show stages such as:
   - board basics
   - balance and turning
   - braking
   - Ollie foundation
   - first street tricks
3. Show each stage with:
   - required actions
   - progress
   - recommended videos
   - next practice task
4. Link AI Coach from a learning step with a prefilled question when possible.
5. Keep training plans and trick check-ins connected in the UI.

Likely frontend touchpoints:

- `frontend/src/views/SkateProfessionalPage.vue`
- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/views/VideoWatchPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Beginner users can follow a visible path rather than browsing isolated content.
- Completed tricks/tasks are shown as progress.
- Learning path links to relevant videos and AI Coach.
- Existing training plan behavior still works.

### Test ideas

- User progress changes after completing a training task.
- Trick check-in updates learning path progress.
- Stage cards render correctly when some linked videos are missing.

## 7. P1-4 Search and Recommendation Foundation

### Objective

Add a simple but useful foundation for discovering content across posts, activities, places, videos, tricks, and news.

### Current state

The project already has separate list APIs and recommendation APIs. P1 should not introduce a heavy search engine unless necessary.

### Required changes

#### Backend

1. Add keyword search support where missing.
2. Normalize search result response shape:
   - type
   - id
   - title
   - summary
   - routePath
   - createTime or updateTime
3. Add a lightweight unified search endpoint if separate endpoints become cumbersome.
4. Keep permissions and visibility rules:
   - no rejected/private/admin-only content
   - only visible posts and public resources
5. Add basic recommendation signals:
   - recent
   - popular
   - same category
   - same city/place
   - user growth or training context when available

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/PublicController.java`
- `src/main/java/com/skatehub/service/PublicContentService.java`
- `src/main/java/com/skatehub/service/RecommendationService.java`
- relevant repositories for posts, places, videos, activities, tricks, and news

#### Frontend

1. Add or refine search input in major discovery pages.
2. Optional: add a unified search panel if routes and APIs support it.
3. Keep result cards compact and type-labeled.
4. Support empty state and loading state.

Likely frontend touchpoints:

- `frontend/src/views/HomePage.vue`
- `frontend/src/views/CommunityPage.vue`
- `frontend/src/views/ActivitiesPage.vue`
- `frontend/src/views/SkateProfessionalPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- Users can search important public content by keyword.
- Search does not expose hidden or rejected content.
- Empty states are clear.
- Existing list pages still work without a search query.

### Test ideas

- Search returns mixed content with correct route paths.
- Rejected/private content is excluded.
- Query with no results returns an empty list, not an error.

## 8. P1-5 Observability and Operational Stability

### Objective

Make the system easier to operate and debug after deployment.

### Current state

The project already includes Spring Boot Actuator dependency and a custom health indicator.

### Required changes

#### Backend

1. Expand health checks where practical:
   - database
   - Redis
   - file storage path
   - AI provider configuration
2. Add consistent logging for high-risk flows:
   - AI calls
   - file uploads
   - report handling
   - activity sign/check-in
3. Add basic metrics or counters if already compatible with current Actuator setup.
4. Avoid exposing sensitive config values in logs or health endpoints.
5. Keep production config environment-driven.

Likely backend touchpoints:

- `src/main/java/com/skatehub/actuator/SkateHubHealthIndicator.java`
- `src/main/java/com/skatehub/config/AiProperties.java`
- `src/main/java/com/skatehub/service/FileStorageService.java`
- `src/main/java/com/skatehub/service/ai/AiGatewayService.java`
- `src/main/resources/application.yml`

#### Frontend

1. Improve error surfaces for:
   - AI timeout
   - upload failure
   - activity action failure
   - auth expiration
2. Keep user-facing messages concise and actionable.
3. Avoid exposing backend stack traces.

Likely frontend touchpoints:

- `frontend/src/api/http.js`
- `frontend/src/components/AppToast.vue`
- `frontend/src/utils/toast.js`
- pages that trigger uploads or AI calls

### Acceptance criteria

- Health endpoint can show whether key dependencies are available.
- Logs contain enough context to debug failures.
- Sensitive values are not logged.
- User-facing errors are clear and do not expose internals.

### Test ideas

- Missing AI config reports degraded health without leaking secrets.
- Upload failure shows a readable toast.
- Expired auth redirects or prompts consistently.

## 9. Constraints

1. Do not weaken existing security or role checks.
2. Do not expose rejected, private, or admin-only content through search or recommendation.
3. Do not auto-moderate destructive actions purely from AI output.
4. Do not let growth points be farmed by repeated identical actions.
5. Keep changes compatible with existing P0 behavior.
6. Prefer small schema additions over broad rewrites.

## 10. Suggested Implementation Style

1. Start each block by reading the relevant controller, service, DTO, and frontend view.
2. Add backend behavior first when the UI depends on new data.
3. Keep frontend UI states complete:
   - loading
   - empty
   - error
   - success
4. Use existing `api` layer patterns instead of calling `http` directly from views.
5. Add tests around business rules, not only happy paths.

## 11. Deliverables

When P1 is finished, the codebase should have:

- stronger report and moderation workflow
- growth system tied to meaningful skate behavior
- clearer learning paths for users
- basic search and recommendation foundation
- better health, logging, and user-facing error handling

## 12. Done Definition

P1 is done when:

- admins can manage reports more efficiently
- users can understand progress and next actions
- beginners can follow a learning path
- public content is easier to discover
- operational failures are easier to diagnose
- existing P0 flows remain stable

