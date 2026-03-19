# Jargoyle Frontend

React SPA for the Jargoyle document explanation tool.

## Tech Stack

- **React 19** + TypeScript
- **Vite** — dev server and build tooling
- **Tailwind CSS v4** — utility-first styling
- **TanStack Query** — server state management
- **React Router** — client-side routing

## Getting Started

```bash
npm install
npm run dev
```

The dev server starts at `http://localhost:5173`. API requests to `/api`, `/oauth2`, `/login`, and `/logout` are proxied to the backend on `http://localhost:8080`.

## Testing

- **Vitest** — test runner (shares Vite's transform pipeline)
- **React Testing Library** — component testing
- **MSW (Mock Service Worker)** — API mocking at the network level

Tests are co-located alongside source files (e.g. `DocumentList.tsx` / `DocumentList.test.tsx`). Shared test infrastructure lives in `src/test/`.

## Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start dev server with HMR |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | Run ESLint |
| `npm test` | Run tests once |
| `npm run test:watch` | Run tests in watch mode |
