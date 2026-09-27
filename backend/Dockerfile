# syntax=docker/dockerfile:1
# Образ бэкенда Dentify (T-PLT-15, PLT-26): сервис app в deploy/compose.dev.yml, позже — прод (T-PLT-25).
# Jar собирается внутри образа, поэтому на чистой машине нужен только Docker: docker compose up (README, «Всё приложение
# в контейнерах»). Слои — по документации Spring Boot («Efficient Container Images» → «Dockerfiles»): зависимости,
# загрузчик и код приложения — отдельными слоями (java -Djarmode=tools … extract --layers), правка кода не тянет заново
# ~100 МБ зависимостей. Профиль задаётся при запуске (SPRING_PROFILES_ACTIVE), в образ не зашит.
# Базовые образы закреплены тегом и digest, как в compose.dev.yml: обновление — осознанное (новый тег + digest из
# `docker buildx imagetools inspect <образ:тег>`).

FROM bellsoft/liberica-openjdk-debian:25.0.4.1@sha256:213512c11989ebdcf464c475603f6381af2a447338a2a04381c14b6e786d8c94 AS build
# unzip — для ./mvnw: без него обёртка берёт tar.gz вместо закреплённого zip и не сходится с его SHA-256.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/main/ src/main/
# Кэш ~/.m2 (Maven и зависимости) живёт между сборками в кэше BuildKit, а не в слое образа. Тесты не компилируются и не
# запускаются: их гоняет ./mvnw verify (./verify.ps1) до сборки образа.
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/dentify-backend-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-debian:25.0.4.1-cds@sha256:20c7cbd6e25c9b9687682b11674769843f845b6435b5ddc7d11440d1b71ea8ed
# Не root: приложению не нужны ни порты ниже 1024, ни запись в образ. curl в образе есть — им проверяет healthcheck.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin dentify
WORKDIR /application
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER dentify
# 8080 — API, 8081 — actuator (наружу не публикуется, только healthcheck изнутри контейнера).
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "application.jar"]
