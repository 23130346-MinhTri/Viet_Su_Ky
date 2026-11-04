# API Contract — `/api/v1`

## Conventions
JSON; DTOs only; validate inputs; paginate lists; consistent errors; auth on protected routes; never expose correct quiz answers before submission.

## Response envelope
```json
{"success":true,"message":"OK","data":{}}
```
```json
{"success":false,"message":"Validation failed","errorCode":"VALIDATION_ERROR","data":{"fieldErrors":[]}}
```

## Auth
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /users/me`
- `PATCH /users/me`

## History
- `GET /eras`
- `GET /eras/{eraId}`
- `GET /history/events`
- `GET /history/events/{eventId}`
- `GET /history/figures`
- `GET /history/figures/{figureId}`
- `GET /history/locations`
- `GET /history/timeline`
- `GET /history/sources/{sourceId}`

Only published content is returned to player routes.

## Campaign and mission
- `GET /campaigns`
- `GET /campaigns/{campaignId}`
- `GET /campaigns/{campaignId}/missions`
- `POST /missions/{missionId}/start`
- `GET /missions/{missionId}/state`
- `POST /missions/{missionId}/actions`
- `POST /missions/{missionId}/complete`

Action payloads express intent. They must not include trusted reward amounts or final completion flags.

## Quiz
- `GET /quizzes`
- `GET /quizzes/{quizId}`
- `POST /quizzes/{quizId}/attempts`
- `POST /quiz-attempts/{attemptId}/answers`
- `POST /quiz-attempts/{attemptId}/submit`
- `GET /quiz-attempts/{attemptId}/result`

Public question DTO omits `isCorrect`; backend scores answers.

## Progress
- `GET /progress/me`
- `GET /progress/me/eras/{eraId}`
- `GET /achievements`
- `GET /achievements/me`
- `GET /rewards/me`

## Admin
- `POST /admin/eras`
- `PATCH /admin/eras/{id}`
- `POST /admin/history/events`
- `PATCH /admin/history/events/{id}`
- `POST /admin/content/{id}/submit-review`
- `POST /admin/content/{id}/approve`
- `POST /admin/content/{id}/reject`
- `POST /admin/content/{id}/publish`

## Idempotency
Use idempotency keys and database uniqueness for operations that grant rewards or mutate progression. Use transactions and return the original result for valid duplicate requests.
