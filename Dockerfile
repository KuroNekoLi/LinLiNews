# Multi-stage build for Spring Boot Kotlin app

FROM gradle:8.10.1-jdk21-alpine AS builder
WORKDIR /workspace
COPY . .
# Build fat jar, skip tests for faster CI
RUN --mount=type=cache,target=/root/.gradle gradle clean bootJar -x test

FROM eclipse-temurin:21-jre
ENV TZ=UTC
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
# Allow passing JVM and Spring profiles via env
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
    SPRING_PROFILES_ACTIVE=default
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
