# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :app:installDist

# --- Runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Non-root runtime user.
RUN useradd --system --uid 10001 gateway
COPY --from=build /workspace/app/build/install/app/ /app/
USER gateway
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["/app/bin/app"]
