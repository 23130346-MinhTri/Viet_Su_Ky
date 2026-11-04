# Game Systems Design

## System ownership
Game engine renders and captures interaction. Backend validates and persists gameplay outcomes.

## Map system
Loads map metadata/tile layers, renders terrain/objects, camera pan/zoom, pointer/touch hit testing, emits selected-object events. It does not grant rewards.

## Entity system
Initial entities: character, building, resource node, interactive location, decoration. Keep definitions data-driven; avoid premature inheritance.

## Mission system
Loads configuration, displays objectives, observes interactions, sends action intent, renders server-confirmed status.

## Resource system
Possible MVP resources: food, wood, materials, knowledge. Quantities and production rates are balancing abstractions, not historical measurements.

## Building system
Show available types, placement preview, send build intent, apply server-confirmed result, display costs/effects.

## Scenario system
Present context/evidence, offer hypothetical options, show simulated outcome and explanation. Clearly label what-if content.

## GameBridge events
`LOCATION_SELECTED`, `BUILDING_SELECTED`, `MISSION_ACTION_REQUESTED`, `KNOWLEDGE_REQUESTED`, `MAP_READY`, `ASSET_LOAD_FAILED`. Keep events typed, minimal and versionable.

## Mission lifecycle
`LOCKED -> AVAILABLE -> IN_PROGRESS -> COMPLETED`; optional `FAILED` only if designed. Backend validates transitions.

## Resource lifecycle
Player intent → authenticate → load server state → validate cost/prerequisites → transaction → return confirmed state → update visuals.

## Scene lifecycle
Create game on route mount; destroy on unmount; remove listeners; avoid duplicate instances in React Strict Mode; handle asset failure.

## MVP build order
1. Empty Phaser scene in React.
2. Static map and camera.
3. Selectable location.
4. React panel reacts to selection.
5. Mission state API.
6. One objective.
7. Simple resource action.
8. Building placement preview.
9. Server-confirmed resource changes.
10. Animation polish.
