# Build the existing dashboard as a preserved fallback route.
FROM node:20-alpine AS legacy-dashboard-builder
WORKDIR /dashboard
COPY backend/dashboard/package*.json ./
RUN npm ci
COPY backend/dashboard/ ./
ENV VITE_BASE=/admin-legacy/
RUN npm run build

# Build the Flutter Web administration panel for the live /admin/ path.
FROM ghcr.io/cirruslabs/flutter:stable AS flutter-dashboard-builder
WORKDIR /dashboard
COPY flutter_dashboard/ ./
RUN flutter create --platforms=web --project-name jeho_admin_flutter . \
 && flutter pub get \
 && flutter build web --release --base-href /admin/ --dart-define=JEHO_API_BASE_URL=https://jeho-own-backend.mgs.blitz.cloud/api/v1/

FROM node:20-alpine AS builder
WORKDIR /app
COPY backend/package*.json ./
RUN npm ci
COPY backend/ ./
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY backend/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/public ./public
# The old dashboard remains available for rollback without affecting API data.
COPY --from=legacy-dashboard-builder /dashboard/dist ./public/admin-legacy
# The Flutter dashboard is now what the existing /admin/ URL serves.
COPY --from=flutter-dashboard-builder /dashboard/build/web ./public/admin
RUN mkdir -p uploads && chown -R node:node /app
USER node
EXPOSE 3000
# Keep the existing isolated JEHO-OWN database startup procedure unchanged.
COPY backend/scripts ./scripts
CMD ["node", "scripts/start-production.js"]
