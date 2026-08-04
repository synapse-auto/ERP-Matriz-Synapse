# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS base
ENV NEXT_TELEMETRY_DISABLED=1

FROM base AS dependencies
WORKDIR /app
RUN apk add --no-cache libc6-compat
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM base AS build
WORKDIR /app
COPY --from=dependencies /app/node_modules ./node_modules
COPY frontend/ ./

# A imagem e a mesma para todos os filhos. Browser usa mesma origem; apenas o
# servidor Next recebe SYNAPSE_BACKEND_URL no runtime para chegar ao backend.
ENV NEXT_PUBLIC_API_URL="" \
    NEXT_PUBLIC_WS_URL=""
RUN --mount=type=cache,target=/root/.npm npm run build

FROM base AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000

RUN addgroup --system --gid 10001 nodejs \
    && adduser --system --uid 10001 --ingroup nodejs nextjs

COPY --from=build --chown=nextjs:nodejs /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER 10001:10001
EXPOSE 3000
HEALTHCHECK CMD wget --quiet --tries=1 --spider \
    http://127.0.0.1:3000/health/liveness || exit 1
CMD ["node", "server.js"]
