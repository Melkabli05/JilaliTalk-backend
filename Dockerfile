# Stage 1: Build the shadow JAR
FROM ghcr.io/graalvm/native-image-community:25-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Runtime on JVM (BC cipher works correctly; no GraalVM native image)
# Alpine is ~5MB vs ~124MB for Ubuntu — smaller attack surface.
# Use eclipse-temurin:25-jre-alpine for minimal image with JVM 25.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /home/app
EXPOSE 8080
COPY --from=builder /workspace/build/libs/jilalibff-0.1-all.jar app.jar

# JVM auto-detects container memory limits (Java 10+).
# Alpine needs certificates for HTTPS calls — Java cacerts bundled in Temurin image.
ENTRYPOINT ["java", "-jar", "app.jar"]
