# Linehaul API - multi stage build.
#
# Both images are build arguments so the JDK can be moved without touching the file:
#   docker build -t linehaul-api .                                  (Java 26)
#   docker build -t linehaul-api \
#     --build-arg BUILD_IMAGE=maven:3.9.11-eclipse-temurin-21 \
#     --build-arg RUNTIME_IMAGE=eclipse-temurin:21-jre .            (Java 21)
ARG BUILD_IMAGE=maven:3.9.11-eclipse-temurin-26
ARG RUNTIME_IMAGE=eclipse-temurin:26-jre

FROM ${BUILD_IMAGE} AS build
WORKDIR /app

# Dependencies first, so a source-only change reuses the cached layer.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY --from=build /app/target/linehaul-1.0.0.jar app.jar

# Everything below is configured with environment variables at run time:
#   MONGODB_URI, PORT, FRONTEND_URL, GEMINI_API_KEY, GEMINI_MODEL, SEED_RESET
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
