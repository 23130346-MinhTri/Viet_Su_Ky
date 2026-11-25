# PHASE 2.4.3.2 → PHASE 2.4.3.3
# DOCUMENT FINALIZATION AND CONTROLLED IMPLEMENTATION

You are the AI Architect and implementation agent for the VIỆT SỬ KÝ project.

Your task is to finish the remaining Phase 2.4.3.2 documentation work and then proceed directly into the implementation work that is already authorized by the project plan.

This is a controlled, sequential task. Do not stop after editing documentation if there is implementation work that can safely proceed.

However, do not bypass historical content approval or invent canonical quiz content.

---

# PART 1 — READ PROJECT CONTEXT FIRST

Before modifying anything, inspect the repository and read:

- `docs/README.md`
- `AI_WORKFLOW.md`
- `PROJECT_CONTEXT.md`
- `PRODUCT_GDD.md`
- `TECHNICAL_DESIGN.md`
- `SRS.md`
- `ARCHITECTURE.md`

Also inspect the existing Phase 2.4.3.1 design, Phase 2.4.3.2 content draft, backend entities, repositories, services, controllers, DTOs, migrations, tests and frontend integration points.

Follow all existing project architecture and security constraints.

Do not modify the approved V3 product direction.

Do not assume previous implementation reports are equivalent to current repository state. Verify the actual code and database migration history before making changes.

# PART 2 — FINALIZE PHASE 2.4.3.2 DOCUMENTATION

Update the latest `PHASE 2.4.3.2 - CANONICAL QUIZ CONTENT DRAFT`.

Apply the following corrections:

## Q1
- Preserve Phong Châu as the proposed answer.
- Preserve the distinction between traditional historical synthesis and archaeological certainty.
- Use the verified museum URL:
  https://baotanglichsu.vn/vi/Articles/4043/68967/nha-nuoc-van-lang-au-lac.html
- Keep human review pending.

## Q2
- Preserve the existing question, answer and explanation.
- Bronze drums must remain representative artifacts, not the only Đông Sơn artifacts.
- Do not add unsupported chronology.
- Do not fabricate academic page references.

## Q3
- Preserve the current commune-level question and proposed answer `Xã Đông Anh`.
- Retain Resolution 1656/NQ-UBTVQH15, Article 1, Clause 114.
- Use:
  https://vanban.chinhphu.vn/?classid=2&docid=214008&pageid=27160
- Add the exact official PDF reference for Clause 114 if accessible.
- Retain 01/07/2025 only with a verifiable official legal or administrative source.
- Remove unrelated historical books from this administrative question.

## Q4
- Preserve the three enclosures: Thành Nội, Thành Trung, Thành Ngoại.
- Use:
  https://dsvh.gov.vn/di-tich-lich-su-kien-truc-nghe-thuat-va-khao-co-co-loa-2958
- Classify this as `Institutional heritage record`, not a primary excavation report.
- Do not overstate archaeological certainty.

## Q5
- Preserve the question and `Thục Phán (An Dương Vương)` option.
- Keep all missing bibliographic details of Đại Việt Sử ký Toàn thư as `PENDING HUMAN REVIEW`.
- Do not fabricate bibliographic information.
- Do not approve Q5 without human confirmation.

## Document status rules
Maintain separate fields:

- `source_verification_status`
- `human_content_approval_status`

A verified source does not automatically approve a question.

The document must retain:

`DRAFT — NOT APPROVED`

Generate the complete revised document, change log, source list and human-review checklist.

# PART 3 — IMPLEMENTATION READINESS GATE

After documentation is updated, evaluate implementation readiness.

There are two independent scopes:

### Scope A — Quiz infrastructure

This work may proceed without approved historical questions:

- Canonical quiz data model.
- Versioned question and option storage.
- Mission-to-question mapping.
- Session question snapshots.
- Answer evidence storage.
- Safe question delivery.
- Answer submission and validation.
- Idempotency.
- Server-side scoring.
- Completion integration.
- Reward and player progress integration.
- Automated tests.

Use only the approved Phase 2.4.3.1 architecture and business rules.

### Scope B — Historical content publication

This work must remain blocked until human approval:

- Publishing actual historical questions.
- Inserting Q1–Q5 as active canonical content.
- Creating production seed data for unapproved questions.
- Marking a question as approved.
- Exposing unapproved content to players.

Do not confuse infrastructure readiness with content approval.

# PART 4 — BEGIN PHASE 2.4.3.3 IMPLEMENTATION

If repository inspection confirms the Phase 2.4.3.1 design is approved and no blocking architectural conflict exists, start implementation immediately.

Do not wait for Q5 historical review to build independent Quiz infrastructure.

Follow the existing implementation work packages:

## WP-A — Canonical quiz model
- Implement versioned quiz questions and answer options.
- Enforce one correct option per published question.
- Enforce immutable released versions.
- Enforce valid mission-question mapping.
- Preserve exactly five questions and fixed order for this Quiz Mission.

## WP-B — Question delivery
Implement:

`GET /api/v1/game-sessions/{sessionId}/quiz/questions`

Requirements:
- Authenticate and verify session ownership.
- Return only the session's question snapshot.
- Never expose correct answers, answer keys, correctness, hidden scoring data or rewards.
- Return answered status and the player's selected option where appropriate.
- Do not mutate game progress during GET requests.

## WP-C — Answer submission
Implement:

`POST /api/v1/game-sessions/{sessionId}/quiz/answers`

Request contains only:
- `eventId`
- `questionId`
- `selectedOptionId`

Never accept client-provided correctness, score, points, completion status or reward.

Enforce:
- Exactly one recorded answer per question.
- Fixed question order.
- No skipping or reordering.
- Incorrect answers are valid evidence and receive zero points.
- Different submissions for an already answered question are rejected.
- Duplicate delivery with the same eventId and request hash returns the stored response.
- Reuse of an eventId with a different request hash returns conflict.

## WP-D — Generic event isolation
For Quiz sessions, the generic gameplay event endpoint must reject quiz-answer attempts with:

`QUIZ_ANSWER_ENDPOINT_REQUIRED`

It must not mutate event count, session activity, progress or answer evidence.

## WP-E — Server-authoritative scoring
- 20 points for a correct answer.
- 0 points for an incorrect answer.
- Maximum score 100.
- Calculate only from persisted canonical answer evidence.
- Recalculate during completion.
- Never trust client score.

## WP-F — Completion
Implement or update:

`POST /api/v1/game-sessions/{sessionId}/complete`

Requirements:
- Require `Idempotency-Key`.
- Reject client `finalScore` and `durationSeconds`.
- Require all five answer records.
- Lock and validate the session.
- Recalculate score on the server.
- Preserve expiration and terminal-session semantics.
- Prevent duplicate rewards and progress updates.
- Store completion response atomically.

## WP-G — Reward and player progress
- Keep quiz score separate from mission reward.
- Use the existing mission reward configuration.
- Apply the approved lock order:
  session → mission progress → player progress.
- Ensure concurrent completion cannot duplicate reward or progress mutations.
- Preserve existing account and player-profile behavior.

## WP-H — Tests and verification
Implement tests for:

- Question delivery authorization.
- No answer-key leakage.
- Fixed question order.
- Invalid question and option membership.
- Duplicate answer replay.
- Conflicting duplicate answer.
- Incorrect answer evidence.
- Five-answer completion requirement.
- Server-side scoring.
- Completion idempotency.
- Duplicate reward prevention.
- Session expiration.
- Concurrent answer submission.
- Concurrent completion.
- PostgreSQL-specific locking and constraints.

Use PostgreSQL integration tests for concurrency and database-specific guarantees.

Do not claim H2 tests prove PostgreSQL concurrency correctness.

# PART 5 — DATABASE AND SECURITY GUARDRAILS

- Inspect existing migrations before creating a new migration.
- Use a new forward-only migration after the latest existing migration.
- Never edit an already applied migration.
- Never drop existing tables or data.
- Never delete Docker volumes.
- Never run destructive database commands against the development database.
- Use a disposable PostgreSQL instance for migration and concurrency verification.
- Do not hardcode credentials, secrets or JWT keys.
- Validate authenticated player ownership on every session operation.
- Keep server authoritative for correctness, score, completion, rewards and progress.
- Reject unknown request fields where the project's API conventions require strict DTO validation.
- Do not trust client metadata for authoritative state.

# PART 6 — IMPLEMENTATION ORDER

Work incrementally:

1. Finalize Phase 2.4.3.2 documentation.
2. Inspect repository and confirm migration baseline.
3. Implement WP-A and its tests.
4. Implement WP-B and its tests.
5. Implement WP-C and its tests.
6. Implement WP-D and its tests.
7. Implement WP-E and WP-F.
8. Implement WP-G.
9. Complete WP-H and full regression verification.

Do not implement all components in one uncontrolled change.

After each work package:
- Run relevant tests.
- Review changed files.
- Report failures and warnings.
- Confirm no unrelated scope was modified.
- Continue to the next package only if its prerequisites pass.

If a blocker appears, stop only the affected work package. Continue independent work where safe.

# PART 7 — REQUIRED FINAL REPORT

Return:

## A. Documentation
- Files modified.
- Q1–Q5 source status.
- Human review status.
- Remaining content approval blockers.

## B. Implementation
- Work packages completed.
- Files created or modified.
- Migration version and purpose.
- APIs implemented.
- Security and idempotency behavior.

## C. Verification
- Maven test results.
- Frontend test results, if frontend changed.
- PostgreSQL integration/concurrency results.
- Migration validation.
- Regression test results.

## D. Outstanding issues
- Blockers.
- Warnings.
- Deferred work.
- Decisions requiring human approval.

## E. Phase decision
Report separate statuses:

- Phase 2.4.3.2 — Content status.
- Phase 2.4.3.3 — Infrastructure implementation status.
- Historical content publication readiness.

Do not mark the overall Quiz Mission as approved merely because the infrastructure implementation passes.

START NOW. Do not stop after producing a plan. Inspect the repository, update the documentation, and begin the first implementation work package if its readiness gate passes.