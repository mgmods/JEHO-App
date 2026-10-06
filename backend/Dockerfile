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
CMD ["sh", "-c", "ready=$(node -e 'const { Client } = require(\"pg\"); (async()=>{const c=new Client({connectionString:process.env.DATABASE_URL}); try{await c.connect(); const r=await c.query(\"select exists (select 1 from information_schema.tables where table_schema=chr(106)||chr(101)||chr(104)||chr(111)||chr(95)||chr(111)||chr(119)||chr(110) and table_name=chr(117)||chr(115)||chr(101)||chr(114)||chr(115)) as ready\"); process.stdout.write(r.rows[0].ready ? \\\"1\\\" : \\\"0\\\");} catch(e){process.stdout.write(\\\"0\\\");} finally{await c.end().catch(()=>{});}})();'); if [ \\\"$ready\\\" = \\\"1\\\" ]; then echo \\\"JEHO-OWN database ready — skipping boot seed\\\"; else echo \\\"JEHO-OWN database not initialized — running seed once\\\"; node dist/database/seed.js; fi && node dist/main.js"]
