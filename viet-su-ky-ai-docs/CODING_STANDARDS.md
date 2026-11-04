# Coding Standards

## General
Readable code, focused functions, meaningful names, minimal dependencies, no secrets in source control. Follow existing conventions unless they conflict with this spec.

## TypeScript/React
Strict mode; avoid `any`; functional components; API calls in service/query layers; TanStack Query for server state if adopted; Zustand only for shared client state; clean subscriptions and Phaser instances; no complex game rules in components; semantic accessible HTML.

## Phaser
Scenes own lifecycle/rendering; systems own reusable behavior; adapters bridge app services and game events; no direct React store mutation; no backend secrets; clean up listeners/resources; client state is untrusted.

## Java/Spring
Constructor injection; request/response DTOs; Bean Validation; thin controllers; business rules in services/domain components; repositories encapsulate persistence; transactions for multi-step state changes; centralized exception mapping; never expose JPA entities.

## Database
Flyway migrations; never edit an applied migration; constraints for invariants; indexes based on query patterns; UTC for technical timestamps; uncertain ancient chronology must not be forced into exact timestamps.

## Naming
React components and Java classes PascalCase; JS/Java methods camelCase; SQL snake_case; stable content codes such as `VAN_LANG_CHAPTER_01`.

## Commit examples
`feat: add chapter browsing`
`fix: prevent duplicate mission rewards`
`test: cover quiz scoring`
`docs: update mission lifecycle`
`refactor: isolate Phaser bridge`
