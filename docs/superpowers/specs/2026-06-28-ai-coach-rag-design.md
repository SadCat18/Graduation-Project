# AI Coach RAG Design

## Context

SkateHub already has an AI coach module for skateboard teaching conversations. The current backend flow is centered on `AiCoachAssistantService`: it validates the chat request, resolves the user session, builds an `AiRequest`, calls `AiGatewayService`, parses the JSON response, saves user and assistant messages, and returns suggestions to the frontend.

The project also has `AiCoachContentRecommendService`, which recommends related posts, videos, and activities by keyword scoring after a question and reply are available. This is useful for "related content", but it does not yet ground the AI coach's answer before generation.

RAG should be introduced as a retrieval layer before the model call. The AI coach will first retrieve relevant SkateHub knowledge, then answer with that knowledge as context. This improves professional accuracy, reduces fabricated platform content, and makes answers traceable to site resources.

## Goal

Add a first-version Retrieval-Augmented Generation flow to the AI coach module:

- Retrieve relevant station-owned skateboard knowledge before generating an answer.
- Ground AI coach replies in tricks, training plans, training tasks, videos, posts, equipment, and selected safety FAQ content.
- Return references with each answer so the frontend can show the sources used.
- Preserve the existing chat/session behavior and existing async related-content flow.
- Keep the first version simple enough for the graduation project demo, while leaving a clear path to vector search.

## Scope

Included:

- A backend RAG service that retrieves top knowledge chunks for an AI coach question.
- A normalized knowledge chunk model that can be built from existing SkateHub data.
- Prompt changes that tell the model how to use retrieved references.
- Response changes that expose answer references to the frontend.
- A frontend reference display under AI coach replies.
- Keyword retrieval as the first implementation path.
- Optional embedding fields and service boundaries for a later semantic retrieval upgrade.

Excluded from the first version:

- External web search during ordinary coach chat.
- Full vector database infrastructure such as Milvus or Elasticsearch.
- Automatic medical diagnosis or injury treatment planning.
- Paid course generation, coach marketplace logic, or personalized long-term training plan generation beyond existing platform data.
- Replacing `AiCoachContentRecommendService`; it remains responsible for related content after the answer.

## Approach Options

### Option A: Keyword RAG First

Create a focused `AiCoachRagService` that searches a unified knowledge chunk table or a small set of repositories by keywords and intent scoring. It returns the top chunks, which are inserted into the AI coach prompt before the user's current question.

Trade-off: fastest to implement and easiest to demonstrate. Retrieval quality is good for explicit terms such as "ollie", "brake", "helmet", and "training plan", but weaker for semantic matches.

### Option B: Database-Backed Vector RAG

Create embeddings for each knowledge chunk, store them in MySQL as serialized vectors or a JSON text field, and compute cosine similarity in Java for the first version. The same service can later move to pgvector, Milvus, or Elasticsearch if data volume grows.

Trade-off: better semantic retrieval, but requires embedding calls, background synchronization, and more testing.

### Option C: Full Search Platform

Introduce a dedicated search engine or vector database immediately, with indexing jobs, hybrid retrieval, reranking, and search observability.

Trade-off: best long-term capability, but too large for the current module and graduation-project timeline.

## Decision

Use Option A for the first implementation and design the boundaries so Option B can be added later. Keyword RAG matches the current repository style, avoids new infrastructure, and still demonstrates the essential RAG idea: retrieve site knowledge first, answer second, and show references.

## Backend Design

Add a new service package surface under the existing AI module:

- `AiCoachRagService`: owns retrieval for coach chat.
- `AiCoachKnowledgeSyncService`: builds or refreshes knowledge chunks from domain tables.
- `AiCoachRagContext`: DTO containing retrieved chunks and formatted prompt text.
- `AiCoachRagChunk`: DTO for one retrieved reference.
- `AiCoachReferenceResponse`: response item returned to the frontend.

The main chat flow becomes:

1. `AiCoachAssistantService.chat()` receives `AiCoachChatRequest`.
2. The service resolves the current AI coach session.
3. `AiCoachRagService.retrieve(request.getMessage(), currentUser)` returns top chunks.
4. `buildConversationMessages(...)` adds the RAG context after the session intro and before recent chat history or the current question.
5. `AiGatewayService.chat(...)` generates the answer from the system prompt, chat history, current question, and retrieved knowledge.
6. The parsed `AiCoachChatResponse` is normalized and enriched with references from the selected chunks.
7. Existing message persistence continues to save the user question and the assistant reply.

`AiCoachContentRecommendService` remains separate. It can still be called by the frontend after the answer to recommend posts, videos, and activities. RAG references are not "recommendations"; they are the evidence used to produce the answer.

## Knowledge Sources

The first RAG index should use data that already exists in the project:

- `SkateTrick`: trick name, difficulty, description, key points, safety tips if available.
- `TrainingPlan`: plan title, level, goal, duration, description.
- `TrainingTask`: task title, description, sequence, plan relation.
- `Video`: title, intro, tags, URL metadata.
- `Post`: approved or visible community posts, title, category, trimmed content.
- `SkateEquipment`: equipment name, type, suitable level, description.
- `Activity` or `SkateEvent`: visible training activities, city, type, time, and description.
- Safety FAQ seed data: short curated text for falls, pain, protective gear, and beginner safety.

For the first version, prioritize `SkateTrick`, `TrainingPlan`, `TrainingTask`, `Video`, and `Post`. Equipment and activities can be added after the core teaching path is stable.

## Data Model

Add a normalized knowledge chunk table:

```sql
CREATE TABLE tb_ai_knowledge_chunk (
    chunk_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    tags VARCHAR(500),
    route_path VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    embedding MEDIUMTEXT,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_ai_chunk_source (source_type, source_id),
    INDEX idx_ai_chunk_status_time (status, update_time)
);
```

Field meaning:

- `source_type`: `TRICK`, `TRAINING_PLAN`, `TRAINING_TASK`, `VIDEO`, `POST`, `EQUIPMENT`, `ACTIVITY`, or `FAQ`.
- `source_id`: original row id from the source table. FAQ rows can use a local seed id.
- `title`: display title and retrieval signal.
- `content`: retrieval text passed to the model when selected.
- `tags`: comma-separated keywords for first-version matching.
- `route_path`: frontend route used when the reference is clicked.
- `embedding`: reserved for the later vector retrieval upgrade.
- `status`: `ACTIVE` chunks can be retrieved; disabled or deleted source data should mark chunks inactive.

The first implementation can generate chunks with a startup or admin-triggered sync job. A later version can refresh a chunk whenever source data changes.

## Retrieval Design

`AiCoachRagService.retrieve(...)` should:

1. Normalize the user question by trimming, lowercasing English text, and keeping Chinese terms intact.
2. Extract intent signals such as trick practice, beginner learning, safety, equipment, training plan, video tutorial, or activity.
3. Search active knowledge chunks.
4. Score title, tags, content, and source type.
5. Return at most five chunks, with a total prompt budget around 1,500 Chinese characters.

Initial scoring can be simple:

- Title keyword match: high score.
- Tag match: high score.
- Content match: medium score.
- Source type boost based on intent.
- Recent or curated content boost for posts and videos.
- Safety FAQ boost when the question contains pain, fall, injury, bleeding, swelling, or similar terms.

If no chunk reaches a minimum score, the prompt should explicitly say that no strong station-owned reference was found. The AI coach may still answer general skateboard learning questions, but must not invent platform-specific resources.

## Prompt Design

Extend the AI coach system prompt with RAG rules:

- Use the station reference material when it is relevant.
- Do not claim that SkateHub has a trick, video, plan, or activity unless it appears in the provided references.
- If references are weak or missing, say the platform reference is limited and give general safe guidance.
- For injury, pain, or recovery questions, provide cautious safety advice and recommend professional medical help for serious symptoms.
- Return valid JSON only.

The model response shape becomes:

```json
{
  "reply": "string",
  "suggestions": ["string", "string"],
  "references": [
    {
      "sourceType": "TRICK",
      "sourceId": 1,
      "title": "Ollie basic practice",
      "routePath": "/skate/tricks/1"
    }
  ]
}
```

The RAG context should be formatted as a compact source list:

```text
Station reference material:
[1] Type: TRICK
Title: Ollie basic practice
Content: ...
Route: /skate/tricks/1

[2] Type: VIDEO
Title: Beginner ollie breakdown
Content: ...
Route: /videos/12
```

The assistant response may cite references naturally in the answer, but the frontend source cards should come from the structured `references` field rather than text parsing.

## API And Response Design

The existing coach chat endpoint can keep the same URL and request shape. Extend only the response payload:

- `reply`: generated answer.
- `suggestions`: follow-up questions.
- `sessionId`: existing session id.
- `references`: selected RAG references.
- Existing related post, video, and activity fields remain available if already used by the frontend.

No separate public RAG endpoint is required for the user-facing chat flow. An admin-only or developer-only sync endpoint can be added later if manual reindexing is useful.

## Frontend Design

The AI coach chat UI should show reference cards directly under each assistant answer when `references` is not empty.

Each card displays:

- Source type label, such as action, plan, video, post, or FAQ.
- Source title.
- Optional short source description if returned later.
- Click behavior using `routePath` when available.

Empty references should not render an empty section. The reply itself remains the main teaching content, and related content can still be loaded asynchronously as it is today.

## Error Handling And Safety

- If knowledge retrieval fails, log the retrieval error and continue the chat without RAG context. The user should not lose the AI coach feature because retrieval is temporarily unavailable.
- If the AI provider fails, keep the existing `BizException` behavior.
- If the model returns invalid JSON, preserve the existing fallback parsing path and set references from the retrieved chunks when possible.
- Do not expose inactive, deleted, private, rejected, or admin-only content through RAG.
- Community posts used as references should be limited to visible/approved posts.
- Personal user data should not be indexed as general knowledge.
- Injury-related answers must stay cautious and avoid diagnosis.

## Testing

Backend tests:

- `AiCoachRagService` returns relevant chunks for explicit trick, training, equipment, and safety questions.
- Retrieval excludes inactive chunks and non-visible community content.
- Retrieval caps the number of chunks and total context length.
- `AiCoachAssistantService` injects RAG context into the model request before generation.
- Chat response includes references matching retrieved chunks.
- Chat still works when retrieval returns no chunks.
- Chat still reports provider failure with the existing error path.

Frontend tests:

- AI coach response rendering shows reference cards when references exist.
- Empty references do not create an empty UI block.
- Clicking a reference uses `routePath` when available.
- Existing related-content behavior remains separate from references.

## Migration And Rollout

1. Add `tb_ai_knowledge_chunk` schema and seed/sync support.
2. Implement keyword retrieval with existing repositories or the knowledge chunk table.
3. Inject RAG context into AI coach chat requests.
4. Extend response DTOs and frontend rendering for references.
5. Add tests for retrieval, prompt injection, and frontend reference cards.
6. Add optional embedding generation after the keyword version is stable.

Rollout should keep the feature enabled by default only after the first sync succeeds. If the chunk table is empty, the AI coach should fall back to normal chat and log that no RAG references were available.

## Future Vector Upgrade

The later semantic version can reuse the same table and service interface:

- Add `AiEmbeddingService` to call the configured provider's embedding API.
- Store one embedding per active knowledge chunk.
- Generate an embedding for the user's question.
- Compute cosine similarity in Java for small data sets.
- Combine vector similarity with keyword score for hybrid retrieval.
- Move to a vector database only if the number of chunks or latency makes MySQL plus Java scoring insufficient.

This keeps the first version practical while preserving a credible path to a more advanced RAG system.

## Acceptance

The feature is accepted when a logged-in user can ask the AI coach a skateboard learning question, receive an answer grounded in relevant SkateHub content, and see clickable reference cards for the source material. The chat must continue to support session history, suggestions, provider error handling, and existing related-content behavior.
