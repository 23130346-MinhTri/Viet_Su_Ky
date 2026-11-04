# Software Requirements Specification — SRS V3

## Roles
- `PLAYER`: play, learn and view own progress.
- `CONTENT_EDITOR`: create/edit draft content.
- `CONTENT_REVIEWER`: review and approve/reject.
- `ADMIN`: manage system and users.

## Functional requirements
### Authentication
- Register, login, logout, view/update permitted profile fields.
- Protected APIs require authentication.
### History
- Browse published eras, events, people, places and timeline.
- Display source/provenance.
- Draft/rejected content is hidden from players.
### Campaign
- Browse campaigns and missions.
- Start/resume missions.
- Backend validates completion.
### Quiz
- Start attempt, submit answers, get server-calculated result and explanations.
- Correct answers are not exposed before submission.
- Attempts belong to authenticated player.
### Gameplay
- Load authorized map and interact with supported objects.
- Client sends intent, not trusted final reward/state.
- Backend validates resources and objectives.
### Progress
- Persist mission progress.
- View chapter progress.
- Grant achievements server-side.
- Duplicate requests do not duplicate rewards.
### Admin
- Create/edit drafts, attach sources, review, approve/reject/publish.
- Audit important actions.

## Non-functional requirements
- Secure password hashing and server-side authorization.
- Never log secrets.
- Common read API target P95 <500 ms under agreed test load; measure.
- Progressive asset loading where practical.
- Browser refresh must not erase persisted progress.
- Core flows support desktop and touch.
- Schema changes use migrations.
- Critical rules have automated tests.
- Published historical content has provenance and review status.

## Acceptance scenarios
1. A player submits a quiz; backend calculates and stores result and returns explanations.
2. A player completes all validated mission objectives; backend completes mission and grants reward exactly once.
3. A player requests published history content; source metadata is returned; drafts remain hidden.
4. A player signs in again; persisted progress is restored.

## Out of scope
Multiplayer, real-time PvP, advanced RTS combat, native apps, full LMS integration, and automatic publication of AI-generated historical claims.
