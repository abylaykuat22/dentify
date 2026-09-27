# syntax=docker/dockerfile:1
# Образ фронтенда Dentify (T-PLT-15, PLT-26): сервис web в deploy/compose.dev.yml. Production-сборка SPA (без моков)
# отдаётся Caddy, как в проде: один origin, /api — прокси на бэкенд. Конфиг Caddy в образ не зашит — его монтирует
# Compose (deploy/caddy/Caddyfile.app; прод — свой Caddyfile, T-PLT-25). На машине нужен только Docker.
# Базовые образы закреплены тегом и digest, как в deploy/compose.dev.yml.

FROM node:23.11.1-alpine3.22@sha256:a34e14ef1df25b58258956049ab5a71ea7f0d498e41d0b514f4b8de09af09456 AS build
# pnpm той же ветки, что у разработчиков (README: Node 23 и pnpm 10); версия закреплена.
RUN npm install --global --no-fund --no-audit pnpm@10.6.4
WORKDIR /app
# Сначала только манифесты: слой с зависимостями пересобирается, лишь когда они поменялись. Хранилище pnpm — в кэше
# BuildKit, а не в образе.
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM caddy:2.11.4-alpine@sha256:6aeddd44c3078b0f9a35206472a11420648a79c184603ef95957d0a20044cb2b
COPY --from=build /app/dist /srv
