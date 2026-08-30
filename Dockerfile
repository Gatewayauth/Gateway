# --- Build stage ---
FROM eclipse-temurin:25-jdk@sha256:e787e08ef76f4c16866108cd7f9fcd96a68eef3ac6cc76866897d4d02d5a2262 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :app:installDist

# --- Runtime stage ---
FROM eclipse-temurin:25-jre@sha256:f9e65324a37f28209ce7dd0e5149a7aa954520ed936fb87813cf6ded2400a112 AS runtime
WORKDIR /app
# Non-root runtime user.
RUN useradd --system --uid 10001 gateway
COPY --from=build /workspace/app/build/install/app/ /app/
USER gateway
EXPOSE 8080
ENV PORT=8080
# Liveness probe: the jre image has no curl/wget, so use a bash TCP open on the
# app port. Fails (non-zero) if nothing is listening.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["/app/bin/app"]
