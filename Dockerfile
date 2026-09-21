# --- Build stage ---
FROM eclipse-temurin:24-jdk@sha256:7493205ffe6caa8074fa8a06a276bb1c5ac41d3dd0fd43a0db66d7f776e80b3e AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :app:installDist

# --- Runtime stage ---
FROM eclipse-temurin:24-jre@sha256:8cb2387a28af84cf0db0948d9c67d4480192f4e567027a3963f145d218e8b4f2 AS runtime
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
