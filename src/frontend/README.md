# Jargoyle Frontend

React SPA for the Jargoyle document explanation tool.

## Tech Stack

- **React 18** + TypeScript
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

## Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start dev server with HMR |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | Run ESLint |
