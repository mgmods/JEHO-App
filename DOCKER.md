# JEHO CHAT — Docker

This Docker setup keeps the source application intact and runs the server stack with:

- NestJS backend
- PostgreSQL 16
- Redis 7
- persistent PostgreSQL, Redis and upload volumes
- Socket.IO/WebSocket support through the backend container

## Start

1. Copy `docker-compose.env.example` to `.env`.
2. Put production secrets and ZEGOCLOUD credentials in `.env`.
3. Start:

```bash
docker compose up -d --build
```

The API is exposed on:

```
http://localhost:3000
```

Swagger:

```
http://localhost:3000/api/docs
```

## Stop

```bash
docker compose down
```

The data volumes are intentionally preserved. To remove the database, Redis and uploads as well:

```bash
docker compose down -v
```

## Production

Do not commit the real `.env`. Put the production secrets in the hosting provider's secret/environment settings.

The Android APK is not itself a Docker container. It remains the native Android client and connects to the Dockerized API.

The Dashboard remains the source Vue/Vite application under `backend/dashboard/`; it can be built separately with:

```bash
cd backend/dashboard
npm ci
npm run build
```

The backend Docker image already uses the source `backend/Dockerfile`.
