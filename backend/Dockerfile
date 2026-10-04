FROM node:20-alpine AS dashboard-builder

WORKDIR /dashboard

COPY dashboard/package*.json ./
RUN npm ci

COPY dashboard/ ./
ENV VITE_BASE=/admin/
RUN npm run build

FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production

COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/public ./public
COPY --from=dashboard-builder /dashboard/dist ./public/admin

RUN mkdir -p uploads && chown -R node:node /app

USER node

EXPOSE 3000

# JEHO-OWN keeps its database isolated in the "jeho_own" schema.
# Seed first so TypeORM creates/updates all tables and default settings
# before Nest starts serving requests. The compiled seed is in dist/.
CMD ["sh", "-c", "node dist/database/seed.js && node dist/main.js"]
