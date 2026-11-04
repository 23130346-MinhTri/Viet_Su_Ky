# VIỆT SỬ KÝ — AI Documentation Pack
Version 3.0 | Vietnamese-first | Web 2.5D educational strategy game

## Fixed product decisions
- Audience: both students and history enthusiasts.
- Gameplay: hybrid learning + strategy + management + missions.
- Visual style: 2.5D, isometric/oblique.
- Historical scope: chronological framework beginning with Văn Lang – Âu Lạc.
- MVP: two introductory chapters, Văn Lang and Âu Lạc; do not build every era in depth.
- Frontend: React + TypeScript + Vite.
- Game engine: Phaser 3 embedded in React.
- Backend: Java + Spring Boot.
- Database: PostgreSQL; Flyway migrations.
- Architecture: modular monolith first.
- Deployment: Docker + Nginx.

## AI reading order
1. AI_WORKFLOW.md
2. PROJECT_CONTEXT.md
3. PRODUCT_GDD.md
4. TECHNICAL_DESIGN.md
5. SRS.md
6. ARCHITECTURE.md
7. DATABASE_DESIGN.md
8. API_CONTRACT.md
9. GAME_SYSTEMS.md
10. HISTORICAL_CONTENT_GUIDELINES.md
11. UI_UX_GUIDELINES.md
12. CODING_STANDARDS.md
13. TESTING_STRATEGY.md
14. SECURITY.md
15. ROADMAP.md
16. DECISIONS.md
17. AI_TASK_PROMPTS.md

## Rules
- Inspect the actual repository before changing code; never assume it is empty.
- Do not silently change fixed product decisions.
- Do not invent historical facts, citations, dates, quotes, or sources.
- Separate documented facts, interpretations, traditions/legends, debates, and gameplay fiction.
- Never present hypothetical gameplay outcomes as historical fact.
- Keep authoritative scores, rewards, unlocks, mission completion, and persistent resources on the backend.
- Phaser must not access the database or own application routing.
- Prefer small, tested vertical slices over giant scaffolds.
- Update docs when architecture or behavior changes.

## Suggested repository
```text
viet-su-ky/
├── docs/
├── frontend/
├── backend/
├── database/
├── assets/
├── infra/
├── scripts/
└── README.md
```
