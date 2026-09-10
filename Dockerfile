# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /workspace/backend

COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
RUN chmod +x ./gradlew

COPY backend/src ./src

RUN ./gradlew bootJar --no-daemon -x test \
    && JAR_PATH="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /workspace/backend.jar


FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend

ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_STATIC_EXPORT=true
ENV NEXT_PUBLIC_API_BASE_URL=""

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./

RUN npm run build \
    && test -f out/index.html \
    && test -f out/sw.js \
    && test -f out/manifest.webmanifest \
    && test -f out/my/index.html


FROM caddy:2-alpine AS caddy-bin


FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

ENV BACKEND_PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0"

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=caddy-bin /usr/bin/caddy /usr/bin/caddy
COPY --from=backend-build /workspace/backend.jar /app/backend.jar
COPY --from=frontend-build /workspace/frontend/out /srv

COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY deploy/start.sh /app/start.sh

RUN chmod +x /app/start.sh \
    && chown -R app:app /app /srv /etc/caddy

USER app

EXPOSE 3000

CMD ["/app/start.sh"]
