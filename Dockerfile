# --- Build stage ---
FROM eclipse-temurin:21-jdk@sha256:85f00967bcc624fc19fa9c2cf124ea426a5363898e267141726f31f358c2e14b AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :app:installDist

# --- Runtime stage ---
FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037 AS runtime
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
