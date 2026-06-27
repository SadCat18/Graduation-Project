# Skate Professional Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first-version SkateHub professional skateboarding module across backend, database, and frontend.

**Architecture:** Add focused JPA entities, repositories, services, and controllers matching the existing Spring Boot style. Add a single Vue route `/skate` with tabs for the four submodules and centralized API wrappers.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, JUnit 5, Mockito, Vue 3, Vue Router, Axios, Node CJS tests.

---

### Task 1: Backend Domain Model

**Files:**

- Create backend entities under `src/main/java/com/skatehub/pojo/skate/`
- Create repositories under `src/main/java/com/skatehub/dao/`
- Create request DTOs under `src/main/java/com/skatehub/pojo/skate/`

- [ ] Add JPA entities for tricks, check-ins, plans, tasks, user plans, records, events, signups, equipment, and favorites.
- [ ] Add repository query methods for the service flows.
- [ ] Add request DTOs with validation annotations for check-ins and task completion.

### Task 2: Backend Services With Tests

**Files:**

- Create `src/main/java/com/skatehub/service/TrickService.java`
- Create `src/main/java/com/skatehub/service/TrainingPlanService.java`
- Create `src/main/java/com/skatehub/service/SkateEventService.java`
- Create `src/main/java/com/skatehub/service/EquipmentService.java`
- Create focused tests in `src/test/java/com/skatehub/service/`

- [ ] Write failing tests for trick check-in experience and history.
- [ ] Implement `TrickService`.
- [ ] Write failing tests for joining/completing training plans.
- [ ] Implement `TrainingPlanService`.
- [ ] Write failing tests for event signup, capacity, cancel, and check-in.
- [ ] Implement `SkateEventService`.
- [ ] Write failing tests for equipment favorite idempotency.
- [ ] Implement `EquipmentService`.

### Task 3: Backend Controllers

**Files:**

- Create `src/main/java/com/skatehub/controller/SkateController.java`
- Create `src/main/java/com/skatehub/controller/UserSkateController.java`

- [ ] Add `/api/skate/**` catalog and action endpoints.
- [ ] Add `/api/user/**` personal history endpoints.
- [ ] Use `SecurityUtils.currentUser()` for authenticated operations.

### Task 4: Database Schema And Seeds

**Files:**

- Modify `database/init.sql`

- [ ] Add all required tables and indexes.
- [ ] Add demo seed data for tricks, training plans/tasks, events, and equipment.
- [ ] Add new badge seed rows for professional module achievements.

### Task 5: Frontend API And Route

**Files:**

- Modify `frontend/src/api/index.js`
- Modify `frontend/src/router/index.js`
- Modify `frontend/src/App.vue`
- Modify `frontend/src/components/AppIcon.vue`

- [ ] Add API wrapper methods for every PDF endpoint.
- [ ] Add lazy route `/skate`.
- [ ] Add `滑板专业` navigation entry and icon support.

### Task 6: Frontend Page

**Files:**

- Create `frontend/src/views/SkateProfessionalPage.vue`

- [ ] Add tabs for tricks, training, events, and equipment.
- [ ] Add list/detail/action flows for each tab.
- [ ] Add loading, empty, error, and success states.
- [ ] Keep controls compact and responsive.

### Task 7: Verification

**Files:**

- Create frontend CJS tests under `frontend/tests/`

- [ ] Run the backend service tests for the new services.
- [ ] Run existing backend test suite if targeted tests pass.
- [ ] Run frontend CJS tests.
- [ ] Run frontend build.
