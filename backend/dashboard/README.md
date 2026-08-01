# AuraLive Admin Dashboard

Vue 3 + Vite + Bootstrap 5 admin console for AuraLive.

## Setup

```bash
cp .env.example .env
npm install
npm run dev
```

Open http://localhost:5173

API base URL is configured via `VITE_API_URL` (default `http://localhost:3000/api/v1`).

## Stack

- Vue 3 Composition API
- Vue Router
- Pinia (auth token in localStorage)
- Axios
- Chart.js / vue-chartjs
- Bootstrap 5 + Bootstrap Icons
