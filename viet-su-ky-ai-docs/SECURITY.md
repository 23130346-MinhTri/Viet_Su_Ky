# Security and Trust Boundaries

## Untrusted inputs
Browser requests, Phaser events, client-submitted scores/resources, user text and uploaded files.

## Trusted only after backend validation
Authenticated identity, server-calculated quiz results, validated mission progress, server-recorded resource transactions and rewards.

## Authentication
Secure password hashing; HTTPS in production; documented token lifetimes; secure refresh-token handling; rotation/revocation as designed; never log credentials or tokens.

## Authorization
Enforce role and resource ownership server-side. Players access only their own private progress. Separate editor and reviewer privileges. Admin routes require explicit roles.

## Gameplay integrity
Never trust client XP, score, reward, resource quantity, unlock state, completion state or correct answer. Use server validation, transactions, idempotency and constraints.

## Web security
Validate input, use parameterized persistence, narrow CORS, rate-limit sensitive endpoints, protect cookie flows against CSRF as appropriate, encode output, validate upload type/size/access, patch dependencies.

## Privacy
Collect only necessary data. Do not expose emails/account data to players. Define retention/deletion behavior before production. Avoid unnecessary sensitive student data.

## Audit
Record actor, action, target, timestamp, outcome and relevant metadata. Do not log secrets or unnecessary personal information.
