# Skate Professional Module Design

## Goal

Build the first version of the SkateHub professional skateboarding module from the PDF requirements. The module covers trick check-ins, training plans, skate events, and equipment browsing, with user growth experience linked to meaningful actions.

## Scope

Included:

- Trick catalog, trick detail, trick check-in submission, trick check-in history, and public check-in flag.
- Training plan catalog, plan detail, user joining/quitting a plan, current plan progress, task completion, and training records.
- Skate event listing/detail, signup, cancel signup, user event list, and event check-in.
- Equipment listing/detail, filtering, favorite/unfavorite, and user favorite list.
- Growth experience grants through the existing `UserGrowthService` with idempotency.
- A Vue page at `/skate` that provides the first-version user-facing workflow for all four submodules.
- Database schema and seed data for demonstration.

Excluded:

- Paid courses, AI-generated plans, coaching marketplace, equipment transactions, carts, orders, payments, livestreams, judging, and complex offline payment/signup flows.
- Full admin CRUD screens for maintaining tricks, plans, events, and equipment. First-version data is seeded in SQL.

## Backend Design

The backend follows the existing Spring Boot structure:

- `pojo`: JPA entities for each table in the requirement document.
- `dao`: Spring Data JPA repositories with focused query methods.
- `service`: four business services: `TrickService`, `TrainingPlanService`, `SkateEventService`, and `EquipmentService`.
- `controller`: `SkateController` for `/api/skate/**` and `UserSkateController` for `/api/user/**` personal module endpoints.

All personal operations derive `userId` from `SecurityUtils.currentUser()`. The client never sends a user id for user-owned operations.

## Data Model

The tables mirror the PDF:

- `tb_skate_trick`
- `tb_trick_checkin`
- `tb_training_plan`
- `tb_training_task`
- `tb_user_training_plan`
- `tb_user_training_record`
- `tb_skate_event`
- `tb_event_signup`
- `tb_skate_equipment`
- `tb_user_equipment_favorite`

Repeated user operations are controlled with unique constraints where useful:

- `(event_id, user_id)` for event signup.
- `(user_id, equipment_id)` for equipment favorites.
- `(user_plan_id, task_id)` for training task records.

Growth experience remains idempotent through `tb_user_growth_log`:

- Trick check-in: target is the created check-in id.
- Training task completion: target is the task id for that user.
- Full training plan completion: target is the user plan id.
- Event signup and check-in: target is the event id.
- First equipment favorite: target is the user id.

## API Design

Public or login-aware catalog endpoints:

- `GET /api/skate/tricks`
- `GET /api/skate/tricks/{trickId}`
- `GET /api/skate/training-plans`
- `GET /api/skate/training-plans/{planId}`
- `GET /api/skate/events`
- `GET /api/skate/events/{eventId}`
- `GET /api/skate/equipments`
- `GET /api/skate/equipments/{equipmentId}`

Authenticated endpoints:

- `POST /api/skate/tricks/{trickId}/checkin`
- `GET /api/skate/tricks/{trickId}/checkins`
- `GET /api/user/trick-checkins`
- `POST /api/skate/training-plans/{planId}/join`
- `POST /api/skate/training-plans/{userPlanId}/quit`
- `GET /api/user/training-plans/current`
- `POST /api/user/training-tasks/{taskId}/complete`
- `GET /api/user/training-records`
- `POST /api/skate/events/{eventId}/signup`
- `POST /api/skate/events/{eventId}/cancel`
- `GET /api/user/events`
- `POST /api/skate/events/{eventId}/checkin`
- `POST /api/skate/equipments/{equipmentId}/favorite`
- `DELETE /api/skate/equipments/{equipmentId}/favorite`
- `GET /api/user/equipment-favorites`

## Frontend Design

Add one user-facing page:

- Route: `/skate`
- Component: `frontend/src/views/SkateProfessionalPage.vue`
- Navigation label: `滑板专业`

The page uses tabs for:

- 动作打卡
- 训练计划
- 赛事活动
- 装备库

The design keeps the first screen practical: quick stats, module tabs, filters, lists, details, and action forms. Empty, loading, and error states are shown inline.

## Errors And Safety

- Invalid ids, missing records, duplicate submissions, closed event signup, full event capacity, and missing login produce `BizException` messages.
- Public catalog queries only return enabled records.
- Personal endpoints never trust client user ids.
- External equipment links are returned as display links only; no payment or order APIs exist.

## Testing

Backend service tests cover:

- Trick check-in creates history and grants experience.
- Training task completion updates progress and grants task/plan rewards idempotently.
- Event signup rejects closed/full/duplicate cases and supports cancel/check-in.
- Equipment favorite is idempotent and first favorite grants experience once.

Frontend tests cover:

- API wrapper includes all required endpoint names.
- Route and top-level page registration expose `/skate`.
