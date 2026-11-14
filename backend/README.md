# AuraLive Backend

Copy `.env.example` to `.env` and adjust secrets before running.

## Quick start

```bash
# From backend/
cp .env.example .env
docker compose up -d postgres redis
# wait for healthy, schema loads from ../database/schema.sql
npm install
npm run seed
npm run start:dev
```

- API: `http://localhost:3000/api/v1`
- Swagger: `http://localhost:3000/api/docs`
- Socket.io namespace: `/realtime`

Or full stack: `docker compose up --build`
