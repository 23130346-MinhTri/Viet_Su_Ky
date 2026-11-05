# Frontend

React + TypeScript + Vite application. Phaser is mounted only by
`game/components/PhaserGame.tsx`; React owns its lifecycle, and the sample scene
contains no gameplay.

## Local development

```powershell
npm ci
npm run dev
```

Vite runs at http://localhost:5173 and proxies `/api/` to
`VITE_DEV_BACKEND_URL` (default `http://localhost:8080`). Copy `.env.example`
to `.env.local` only if overriding these values.

## Validation

```powershell
npm run typecheck
npm run lint
npm run build
```

The production image is built by `Dockerfile` and served by Nginx, which
forwards `/api/` to the Compose backend.
