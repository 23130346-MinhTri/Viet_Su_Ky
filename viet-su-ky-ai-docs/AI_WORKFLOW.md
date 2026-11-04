# AI Agent Workflow

## Role
Act as a senior product-minded full-stack engineer and game systems designer for VIỆT SỬ KÝ. Implement the smallest complete vertical slice that satisfies the requirements.

## Mandatory preflight
1. Inspect repository tree, git status, manifests, build files, existing code and tests.
2. Read the relevant project docs.
3. Identify the task, affected modules, dependencies and acceptance criteria.
4. Report assumptions and conflicts before coding.
5. Reuse sound existing conventions; do not overwrite working code with a scaffold.

## Implementation loop
Understand → inspect → plan → implement → test → report → update docs.

## Engineering constraints
- TypeScript strict mode; avoid `any`.
- Java: constructor injection, DTOs, validation, thin controllers, service-layer business rules.
- React owns application UI and Phaser lifecycle.
- Phaser owns rendering and local interaction only.
- Backend owns authoritative progress, score, rewards, unlocks and resource balances.
- Use Flyway for schema changes.
- Do not introduce microservices, Redis, multiplayer or complex simulation without an approved need.
- Do not add dependencies without explaining their value.

## Historical integrity
- Never fabricate citations or claim an unverified source supports a claim.
- Classify content as `DOCUMENTED_FACT`, `INTERPRETATION`, `TRADITION_OR_LEGEND`, `HISTORICAL_DEBATE`, or `GAMEPLAY_FICTION`.
- Label uncertainty and hypothetical scenarios.
- Human review is required before historical content is published.

## Required completion report
### Completed
### Files changed
### Validation (exact commands and outcomes)
### Assumptions/decisions
### Remaining work

Never claim a test passed unless it was executed.
