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

# JEHO-OWN production startup checks the existing isolated "jeho_own" schema.
# It deliberately does not seed, reset, or synchronize the live database.
COPY --from=builder /app/scripts ./scripts

CMD ["node", "scripts/start-production.js"]
