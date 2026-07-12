# SkateHub P0 Development Manual for Codex

## 1. Goal

Complete the three highest-priority upgrade blocks for SkateHub:

1. AI Coach enhancement
2. Activity workflow closure
3. Place detail enrichment

The work should keep the current backend/frontend split, reuse existing APIs and view patterns, and avoid unnecessary refactors.

## 2. Project Snapshot

Tech stack:

- Backend: Spring Boot 3.3.5, Java 17, JPA, Security, Redis, MySQL
- Frontend: Vue CLI + Vue 3

Key entry points already in the repo:

- Frontend AI coach page: `frontend/src/views/AiCoachPage.vue`
- Frontend place detail page: `frontend/src/views/PlaceDetailPage.vue`
- Frontend activity detail page: `frontend/src/views/ActivityDetailPage.vue`
- Frontend API layer: `frontend/src/api/index.js`
- AI backend controller: `src/main/java/com/skatehub/controller/AiController.java`
- AI coach backend service: `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- AI retrieval design doc: `docs/superpowers/specs/2026-06-28-ai-coach-rag-design.md`

Existing route patterns:

- `/ai-coach`
- `/places/:id`
- `/activities/:id`

## 3. Work Order

Implement in this order:

1. AI Coach enhancement
2. Activity workflow closure
3. Place detail enrichment

Do not start broad UI polish until the behavior is complete.

## 4. P0-1 AI Coach Enhancement

### Objective

Turn the AI coach into a grounded teaching flow that answers with source references, keeps chat history, and still works when retrieval fails.

### Current state

The frontend already supports:

- chat history
- quick questions
- assistant reply rendering
- related content cards
- reference card area in the message model

The backend already supports:

- unified AI request/response flow
- AI coach session persistence
- related content recommendation
- scene-based AI handling

### Required changes

#### Backend

1. Add or complete a retrieval step before model generation.
2. Return structured references in the AI coach response.
3. Keep chat session persistence unchanged.
4. Keep related-content recommendation separate from references.
5. Add fallback behavior when retrieval fails:
   - chat still answers
   - references may be empty
   - failure is logged

Recommended backend touchpoints:

- `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- `src/main/java/com/skatehub/service/ai/AiCoachRagService.java` if missing or incomplete
- `src/main/java/com/skatehub/pojo/ai/AiCoachChatResponse.java`
- `src/main/java/com/skatehub/pojo/ai/AiCoachReferenceResponse.java`
- `src/main/java/com/skatehub/scene/CoachChatAiSceneHandler.java`

#### Frontend

1. Render source cards under assistant replies when `references` is present.
2. Keep empty reference blocks hidden.
3. Clicking a reference should route through `routePath` when available.
4. Keep related content rendering separate from source references.

Recommended frontend touchpoints:

- `frontend/src/views/AiCoachPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- A logged-in user asks a skate learning question and gets an answer grounded in SkateHub content.
- Assistant replies can show clickable references.
- The chat still works when retrieval returns nothing.
- Provider failures still use the existing error path.

### Test ideas

- Retrieval returns relevant chunks for explicit trick or safety questions.
- Reference cards render only when references exist.
- Empty references do not create an empty UI section.

## 5. P0-2 Activity Workflow Closure

### Objective

Make activity pages support the full loop:

discover -> join -> attend -> check in -> review -> recycle into community content

### Current state

The project already has:

- activity publish
- activity list
- activity detail
- sign-up related APIs
- check-in related APIs
- safety companion hints in detail UI

### Required changes

#### Backend

1. Make activity state transitions clearer.
2. Ensure sign-up, cancel, and check-in states are consistent.
3. Support capacity checks and full-state handling.
4. Add post-event review or summary data if the model already has room for it.
5. Preserve organizer/admin control paths.

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/ActivityController.java`
- `src/main/java/com/skatehub/service/ActivityService.java`
- `src/main/java/com/skatehub/pojo/activity/ActivityCreateRequest.java`
- `src/main/java/com/skatehub/pojo/Activity.java`
- related sign/check-in entities and repositories

#### Frontend

1. Show the current participation state on activity detail pages.
2. Add or refine action buttons for:
   - join
   - cancel
   - check in
   - review after event
3. Show organizer-facing info clearly:
   - sign-up count
   - full / closed state
   - safety hints
4. Add a post-event recap entry point if the backend supports it.

Likely frontend touchpoints:

- `frontend/src/views/ActivityDetailPage.vue`
- `frontend/src/views/ActivitiesPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- User can go through join/cancel/check-in/review without broken state.
- Capacity and status are consistent in both list and detail views.
- Event safety hints remain visible.

### Test ideas

- Full activity flow works for open and full activities.
- Cancelled and checked-in states render correctly.
- Organizer info and sign-up counts stay in sync.

## 6. P0-3 Place Detail Enrichment

### Objective

Turn place detail pages into a decision page that helps users judge:

- whether the spot is suitable
- what it is good for
- what to watch out for
- what related content exists

### Current state

`PlaceDetailPage.vue` already has:

- hero section
- score panel
- tags
- intro blocks
- related activities
- related videos
- review submission
- review gallery

### Required changes

#### Backend

1. Ensure the place detail payload includes structured fields that can support richer front-end logic.
2. Add or refine these fields if they are missing:
   - suitable level
   - obstacle tags
   - safety tips
   - related videos
   - related activities
   - contribution / correction info
3. Support user-submitted place corrections if the model already has a contribution flow.

Likely backend touchpoints:

- `src/main/java/com/skatehub/controller/PlaceContributionController.java`
- `src/main/java/com/skatehub/service/PlaceContributionService.java`
- `src/main/java/com/skatehub/service/PlaceReviewService.java`
- `src/main/java/com/skatehub/service/PublicContentService.java`
- `src/main/java/com/skatehub/pojo/Place.java`

#### Frontend

1. Make the page more decision-oriented.
2. Keep the existing layout, but improve:
   - difficulty / suitability display
   - obstacle and safety tags
   - related activity links
   - related video matching
3. Add a lightweight user contribution/correction entry point if supported.
4. Preserve the review flow and gallery behavior.

Likely frontend touchpoints:

- `frontend/src/views/PlaceDetailPage.vue`
- `frontend/src/api/index.js`

### Acceptance criteria

- A user can tell whether the place fits beginner/intermediate/practice use.
- Safety and obstacle info is easy to scan.
- Related activities and videos are clearly connected.
- The review flow still works.

### Test ideas

- Place detail shows tags and safety hints when data exists.
- Related activities/videos render only when matched.
- Review submission still refreshes the page data.

## 7. Constraints

1. Keep edits scoped to the three P0 blocks.
2. Reuse existing API names where possible.
3. Avoid adding new infra unless the feature truly needs it.
4. Do not break current auth, session, or admin flows.
5. Do not remove related content behavior from AI Coach.
6. Keep fallback behavior safe:
   - AI retrieval failure should not break chat
   - empty related content should not break pages

## 8. Suggested Implementation Style

1. Read the target file before changing it.
2. Find one nearby example before adding a new pattern.
3. Keep DTO changes and UI changes aligned.
4. Update API layer and views together.
5. Add tests for the smallest meaningful behavior first.

## 9. Deliverables

When finished, the codebase should have:

- stronger AI coach responses with references
- a more complete activity lifecycle
- richer place detail decisions and safety context

## 10. Done Definition

The P0 work is done when:

- AI Coach answers can show sources
- activity flow is usable end to end
- place detail helps users decide, not just read
- existing flows still work
- the new behavior is documented and test-covered where practical

