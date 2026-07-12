# AI Coach RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-version keyword RAG flow to the AI coach so answers can include station-owned reference material.

**Architecture:** Add a focused retrieval service that reads existing SkateHub repositories, scores lightweight knowledge chunks, and injects a compact reference context into coach chat prompts. Extend the coach chat response and Vue page to render references separately from existing related-content recommendations.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Lombok, JUnit 5, Mockito, Vue 3, Node contract tests.

---

## File Structure

- Create `src/main/java/com/skatehub/pojo/ai/AiCoachReferenceResponse.java`: response DTO for one RAG source card.
- Create `src/main/java/com/skatehub/service/ai/AiCoachRagChunk.java`: internal retrieval DTO.
- Create `src/main/java/com/skatehub/service/ai/AiCoachRagContext.java`: retrieval result plus formatted prompt text.
- Create `src/main/java/com/skatehub/service/ai/AiCoachRagService.java`: keyword retrieval over tricks, plans, tasks, videos, posts, and safety FAQ.
- Modify `src/main/java/com/skatehub/pojo/ai/AiCoachChatResponse.java`: add `references`.
- Modify `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`: call RAG before model generation, inject context, and return references.
- Modify `src/test/java/com/skatehub/security/DataOwnershipServiceTest.java`: update constructor wiring for new dependency.
- Create `src/test/java/com/skatehub/service/ai/AiCoachRagServiceTest.java`: unit tests for retrieval behavior.
- Create `src/test/java/com/skatehub/service/ai/AiCoachAssistantRagTest.java`: unit tests for prompt injection and response references.
- Modify `frontend/src/views/AiCoachPage.vue`: display references below assistant messages.
- Create `frontend/tests/aiCoachReferences.test.cjs`: source contract test for reference rendering.

## Task 1: Backend RAG Retrieval

**Files:**
- Create: `src/main/java/com/skatehub/service/ai/AiCoachRagChunk.java`
- Create: `src/main/java/com/skatehub/service/ai/AiCoachRagContext.java`
- Create: `src/main/java/com/skatehub/service/ai/AiCoachRagService.java`
- Create: `src/test/java/com/skatehub/service/ai/AiCoachRagServiceTest.java`

- [x] **Step 1: Write failing retrieval tests**

Cover explicit trick retrieval, safety FAQ retrieval, empty question behavior, and max result count.

- [x] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=AiCoachRagServiceTest test`

Expected: compilation fails because the RAG classes do not exist.

- [x] **Step 3: Implement retrieval DTOs and service**

Implement repository-backed keyword retrieval with a maximum of five chunks and formatted context text.

- [x] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd -Dtest=AiCoachRagServiceTest test`

Expected: tests pass.

## Task 2: Coach Chat Prompt Injection

**Files:**
- Create: `src/main/java/com/skatehub/pojo/ai/AiCoachReferenceResponse.java`
- Modify: `src/main/java/com/skatehub/pojo/ai/AiCoachChatResponse.java`
- Modify: `src/main/java/com/skatehub/service/ai/AiCoachAssistantService.java`
- Modify: `src/test/java/com/skatehub/security/DataOwnershipServiceTest.java`
- Create: `src/test/java/com/skatehub/service/ai/AiCoachAssistantRagTest.java`

- [x] **Step 1: Write failing assistant tests**

Verify the model request contains `Station reference material` and the chat response includes `references`.

- [x] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -Dtest=AiCoachAssistantRagTest test`

Expected: compilation fails or assertion fails because chat does not use RAG yet.

- [x] **Step 3: Implement prompt injection and references**

Add `AiCoachRagService` to the assistant, build RAG context before `AiRequest`, inject it into messages, and set references on normalized responses.

- [x] **Step 4: Run focused tests**

Run: `.\mvnw.cmd -Dtest=AiCoachAssistantRagTest,DataOwnershipServiceTest test`

Expected: tests pass.

## Task 3: Frontend Reference Cards

**Files:**
- Modify: `frontend/src/views/AiCoachPage.vue`
- Create: `frontend/tests/aiCoachReferences.test.cjs`

- [x] **Step 1: Write failing source contract**

Assert `AiCoachPage.vue` stores `references`, renders a `coach-references` block, and routes known `routePath` references.

- [x] **Step 2: Run test to verify it fails**

Run: `node frontend/tests/aiCoachReferences.test.cjs`

Expected: fail because the reference UI does not exist.

- [x] **Step 3: Implement reference rendering**

Store references on assistant messages, render compact source cards under the answer, and add CSS for desktop/mobile.

- [x] **Step 4: Run frontend tests**

Run: `node frontend/tests/aiCoachReferences.test.cjs`

Expected: pass.

## Task 4: Verification

**Files:**
- All changed files.

- [x] **Step 1: Run backend focused tests**

Run: `.\mvnw.cmd -Dtest=AiCoachRagServiceTest,AiCoachAssistantRagTest,DataOwnershipServiceTest test`

Expected: all selected backend tests pass.

- [x] **Step 2: Run frontend focused tests**

Run: `node frontend/tests/aiCoachReferences.test.cjs`

Expected: frontend reference contract passes.

- [x] **Step 3: Check diff**

Run: `git diff -- src/main/java src/test/java frontend/src/views/AiCoachPage.vue frontend/tests/aiCoachReferences.test.cjs docs/superpowers/plans/2026-06-29-ai-coach-rag.md`

Expected: diff contains only RAG-related changes.
