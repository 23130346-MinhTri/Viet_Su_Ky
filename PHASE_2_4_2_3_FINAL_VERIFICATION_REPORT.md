# PHASE 2.4.3 — GAMEPLAY EVENT VALIDATION & SERVER-AUTHORITATIVE COMPLETION
# PRE-IMPLEMENTATION AUDIT

## 1. OBJECTIVE

Perform a comprehensive pre-implementation audit of Phase 2.4.3 for the VIỆT SỬ KÝ project.

The objective is to inspect the existing repository, approved architecture, Phase 2.4 lifecycle documents, and completed Phase 2.4.1–2.4.2 implementation.

Identify:
- Existing functionality.
- Missing functionality.
- Security and integrity risks.
- Data consistency risks.
- Dependencies between features.
- Recommended implementation order.
- Test and verification requirements.

This is an AUDIT ONLY task.

Do not implement code.

## 2. PROJECT CONTEXT

Repository:
23130346-MinhTri/Viet_Su_Ky

Working directory:
D:\Games\History

Architecture:
- Modular monolith.
- React + TypeScript + Vite + Phaser 3 frontend.
- Java 17 + Spring Boot backend.
- PostgreSQL 16.
- Docker Compose.
- Nginx.

Backend is authoritative for:
- Gameplay validation.
- Scores.
- Mission completion.
- Rewards.
- Unlocks.
- Persistent player progress.

Frontend and Phaser are responsible for presentation, input, and gameplay interaction. They must not be trusted as authorities for important game state.

## 3. MANDATORY DOCUMENTATION READING

Before inspecting implementation, read the project documentation and AI workflow instructions.

At minimum:

- docs/README.md
- AI_WORKFLOW.md
- PROJECT_CONTEXT.md
- PRODUCT_GDD.md
- TECHNICAL_DESIGN.md
- SRS.md
- ARCHITECTURE.md

Also inspect all relevant Phase 2.4 documents, including:

- PHASE_2_4_GAMEPLAY_LIFECYCLE.md
- PHASE_2_4_FINAL_DESIGN_REVIEW.md
- Phase 2.4.1 reports.
- Phase 2.4.2 reports.
- PHASE_2_4_2_2_FINAL_REPORT.md
- PHASE_2_4_2_3_FINAL_VERIFICATION_REPORT.md
- Existing Phase 2.4 implementation plans and audit reports.

If a document does not exist, report that fact. Do not invent its contents.

Preserve approved V3 product decisions and existing architectural constraints.

## 4. INSPECT THE ACTUAL REPOSITORY

Inspect the current source code, not only documentation.

### A. Gameplay event pipeline

Inspect:
- GameSession entity.
- GameSessionService.
- GameSessionRepository.
- Event DTOs.
- Event controllers.
- MissionProgress entity and service.
- Mission definitions and question/checkpoint data.
- Existing event validation logic.
- Event ordering and session lifecycle rules.

Determine:

1. What event types are currently supported?
2. Which event fields are accepted from the client?
3. Which fields are validated by the backend?
4. Can a client submit arbitrary event types?
5. Can events be replayed?
6. Can events be submitted out of order?
7. Is event ownership validated?
8. Is the session checked for ACTIVE and EXPIRED state?
9. Are invalid events prevented from changing progress?
10. Are duplicate events handled safely?
11. Is event evidence persisted?
12. Can the client manipulate event metadata to influence rewards or scores?

Do not assume that a DTO field is safe merely because it has a validation annotation.

### B. Server-authoritative score calculation

Inspect the current completion flow.

Determine:

- Where finalScore originates.
- Whether the client can submit finalScore.
- Whether the backend recalculates the score.
- Whether canonical question/checkpoint data exists on the backend.
- Whether answers are validated against server-side correct answers.
- Whether score calculation is deterministic.
- Whether partial completion is possible.
- Whether invalid or incomplete sessions can be completed.
- Whether score limits and numeric ranges are enforced.

Explicitly identify every client-controlled field that could affect score, completion, or reward.

### C. Mission completion

Inspect:

- completeSession.
- MissionProgress updates.
- GameSession state transitions.
- Prerequisite handling.
- Mission availability.
- Completion response.
- Transaction boundaries.

Determine whether completion is atomic across:

- Session status.
- Final score.
- Mission progress.
- Player progress.
- Reward issuance.

Identify any possibility of partial completion or inconsistent state.

### D. Reward issuance and idempotency

Inspect:

- PlayerReward entity.
- Reward repository and service.
- IdempotencyRecord entity and service.
- Database unique constraints.
- Completion request handling.
- Existing reward uniqueness rules.

Determine:

1. Whether a duplicate completion can issue duplicate rewards.
2. Whether idempotency is persisted in PostgreSQL.
3. Whether the same key with a different payload is rejected.
4. Whether concurrent requests are handled safely.
5. Whether reward issuance is atomic with mission completion.
6. Whether reward calculations depend on client-controlled data.
7. Whether repeatable and one-time rewards are distinguished correctly.

Do not redesign existing approved reward rules without documenting the reason.

### E. Player progress consistency

Inspect:

- PlayerProgress entity.
- MissionProgress entity.
- Their repositories and services.
- Progress recalculation logic.
- Mission prerequisite logic.

Determine:

- Which values are authoritative.
- Whether derived progress can become inconsistent.
- Whether progress is updated atomically.
- Whether repeated completion changes progress incorrectly.
- Whether concurrent mission completions can cause lost updates.

### F. Game Save

Inspect the existing GameSave design and implementation status.

Determine:
- Whether it is currently implemented.
- Which data belongs in a save snapshot.
- Whether it duplicates mission progress or session state.
- Whether the save can be used to bypass gameplay validation.
- Whether it depends on Phase 2.4.3.

Do not implement GameSave in this audit.

## 5. SECURITY AND DATA INTEGRITY REVIEW

Evaluate the implementation against these principles:

1. Backend-authoritative game state.
2. Client input is untrusted.
3. Ownership comes from authenticated identity.
4. No client-supplied player ID is trusted.
5. Events are validated against canonical server-side definitions.
6. Completion requires validated gameplay evidence.
7. Scores are calculated or verified server-side.
8. Rewards are issued only after successful completion.
9. Idempotency is enforced at the database level where appropriate.
10. Concurrent requests cannot create duplicate rewards or inconsistent progress.
11. Expired and terminal sessions cannot continue gameplay.
12. Failed operations cannot leave partial state changes.

Inspect whether existing expiration locking and transaction semantics remain compatible with the proposed gameplay pipeline.

Do not modify the existing expiration policy.

## 6. DATABASE AND MIGRATION REVIEW

Inspect migrations V1–V9.

Identify:
- Current relevant tables and relationships.
- Existing unique constraints and indexes.
- Missing constraints.
- Potential foreign-key or transaction issues.
- Whether additional migrations are required.

Do not modify V1–V9.

Any schema changes must be proposed as forward-only migrations.

Do not reset, delete, or recreate active development databases or Docker volumes.

## 7. TEST COVERAGE AUDIT

Inspect existing unit, integration, E2E, and PostgreSQL opt-in tests.

Identify coverage gaps for:

- Valid and invalid gameplay events.
- Event ordering.
- Duplicate events.
- Forged client fields.
- Cross-player access.
- Session expiration during gameplay.
- Score calculation.
- Incomplete mission completion.
- Duplicate completion.
- Idempotency key conflicts.
- Concurrent completion.
- Reward uniqueness.
- Player progress consistency.
- Transaction rollback.
- PostgreSQL locking and constraints.

Separate:
- Existing verified tests.
- Existing tests that only cover partial behavior.
- Missing tests.

Do not claim a test passes unless there is actual execution evidence.

## 8. IMPLEMENTATION DEPENDENCY ANALYSIS

Recommend an implementation sequence.

Consider these potential work packages:

A. Gameplay Event Contract & Validation

B. Server-side Gameplay Evidence

C. Server-authoritative Score Calculation

D. Mission Completion Transaction

E. Reward Issuance & Idempotency Verification

F. Player Progress Consistency

G. Game Save compatibility assessment

Do not assume all work packages should be implemented together.

Identify the smallest useful vertical slice that can be implemented and verified independently.

For each work package, report:
- Objective.
- Existing implementation.
- Missing functionality.
- Dependencies.
- Database impact.
- API impact.
- Tests required.
- Risks.

Do not introduce unapproved gameplay features.

## 9. SCOPE RESTRICTIONS

This task is audit only.

STRICTLY FORBIDDEN:

- No production Java code changes.
- No frontend or Phaser changes.
- No database migration changes.
- No active database data modification.
- No Docker volume reset or deletion.
- No dependency upgrades.
- No new gameplay features.
- No reward policy redesign.
- No score policy redesign without documenting the proposed change.
- No GameSave implementation.
- No Phase 2.5 work.
- No unapproved historical content changes.

Preserve existing completed phases.

## 10. REQUIRED OUTPUT

Create:

PHASE_2_4_3_PRE_IMPLEMENTATION_AUDIT.md

The report must include:

1. Executive summary.
2. Documentation inspected.
3. Actual repository implementation inspected.
4. Current architecture and data flow.
5. Gameplay event validation findings.
6. Score calculation findings.
7. Mission completion findings.
8. Reward and idempotency findings.
9. Player progress findings.
10. GameSave dependency assessment.
11. Security and data integrity findings.
12. Database and migration assessment.
13. Existing test coverage.
14. Missing test coverage.
15. Implementation dependency graph.
16. Recommended vertical slice.
17. Proposed implementation order.
18. Risks and unresolved questions.
19. Explicit files likely to change in the first implementation slice.
20. Final audit recommendation.

For every important finding, include:
- File path.
- Relevant class/method.
- Actual behavior.
- Expected behavior.
- Evidence or test reference.

Clearly distinguish verified facts from recommendations and assumptions.

## 11. STOP CONDITION

After creating the audit report:

STOP.

Do not create an implementation plan yet.

Do not modify application code.

Wait for explicit review and approval.

The next step will be a separate implementation-plan task.