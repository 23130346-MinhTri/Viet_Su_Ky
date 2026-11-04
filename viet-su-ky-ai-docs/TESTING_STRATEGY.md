# Testing Strategy

## Test pyramid
Unit tests for rules; integration tests for repositories/transactions/APIs; React component tests; Playwright E2E for player journeys; manual visual checks for Phaser and responsive behavior.

## Frontend
Suggested: Vitest, React Testing Library, Playwright.
Test auth UI, history browsing, loading/error states, quiz feedback, progress, bridge lifecycle, Phaser cleanup, keyboard/touch basics.

## Backend
Suggested: JUnit 5, Mockito, Spring Boot Test, Testcontainers.
Test auth/authorization, publication visibility, quiz scoring, mission transitions, resource validation, reward idempotency, rollback, ownership and progress persistence.

## Game engine
Test asset loading/failure, scene create/destroy, camera, hit testing, bridge events, listener cleanup, API failure behavior.

## Historical QA
Verify source, classification, date uncertainty, answer/explanation consistency, legend/debate labels, simulation labels and Vietnamese spelling.

## Release gates
Do not release if critical security tests fail, duplicate rewards are possible, progress is lost, quiz answers leak before submission, required provenance is missing, or core game route crashes.

## Reporting
Mark each check passed, failed, skipped or not run. Never claim unexecuted tests passed.
