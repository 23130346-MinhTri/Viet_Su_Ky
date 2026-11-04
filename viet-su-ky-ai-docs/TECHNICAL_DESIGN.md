# Technical Design Document — TDD V3

## 1. Architecture
Modular monolith for MVP:
- React + TypeScript + Vite: application UI.
- Phaser 3: embedded game rendering and interaction.
- Java + Spring Boot: APIs and business rules.
- PostgreSQL: persistence.
- Flyway: migrations.
- Docker + Nginx: deployment.

Do not start with microservices. Add Redis or object storage only when justified.

## 2. Responsibilities
**React:** routing, auth UI, dashboard, history, mission panels, quiz, progress, admin, error/loading states.
**Phaser:** map, camera, sprites, scenes, animation, local input.
**Spring Boot:** auth, authorization, content, missions, quiz scoring, resource transactions, rewards, unlocks, progress, admin.
**PostgreSQL:** users, content, sources, missions, questions, attempts, resources, progress, achievements and audit.

## 3. Boundaries
- Phaser never accesses the database.
- React does not calculate authoritative rewards.
- Backend does not depend on Phaser internals.
- GameBridge communicates typed events between React and Phaser.
- Historical content is data-driven, not hard-coded in scenes.

## 4. Frontend structure
```text
frontend/src/
├── app/{providers,layouts,router.tsx}
├── features/{auth,dashboard,eras,history,campaigns,missions,learning,quiz,progress,achievements,admin}
├── game-engine/{config,scenes,maps,entities,systems,events,adapters}
├── shared/{components,hooks,services,types,utils}
├── store/
└── styles/
```

## 5. Backend modules
`common`, `config`, `auth`, `user`, `era`, `history`, `figure`, `location`, `campaign`, `mission`, `gameplay`, `resource`, `learning`, `quiz`, `progression`, `achievement`, `admin`, `audit`.

Use controller/service/repository/entity/dto/mapper packages where needed; avoid empty boilerplate.

## 6. React–Phaser bridge
```ts
export type GameEvent =
  | { type: "BUILDING_SELECTED"; buildingId: string }
  | { type: "LOCATION_SELECTED"; locationId: string }
  | { type: "MISSION_ACTION_REQUESTED"; actionCode: string }
  | { type: "KNOWLEDGE_REQUESTED"; topicId: string };

export interface GameBridge {
  emit(event: GameEvent): void;
  subscribe(listener: (event: GameEvent) => void): () => void;
}
```
Bridge carries intent/presentation events only. React owns game instance creation and destruction. Clean up listeners on unmount.

## 7. Phaser scenes
`BootScene`, `PreloadScene`, `WorldMapScene`, `CampaignScene`, `SettlementScene`, `ScenarioScene`. Build only scenes required by current vertical slice.

## 8. State ownership
Client: camera, selected object, modal state, animation and visual effects.
Server: identity, mission status, quiz results, resources, rewards, XP, unlocks and persistent progress.

## 9. Infrastructure
MVP: frontend static build, Spring Boot, PostgreSQL, Nginx. Add other services only when required.

## 10. Quality gates
Module boundaries respected; DTOs used; migrations versioned; critical business rules tested; client values never trusted for authoritative state.
