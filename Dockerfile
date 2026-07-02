# Stage 1: Build the shadow JAR
FROM ghcr.io/graalvm/native-image-community:25-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Run on JVM (no native image — BC cipher works correctly on JVM)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /home/app
EXPOSE 8080
COPY --from=builder /workspace/build/libs/jilalibff-0.1-all.jar app.jar
ENTRYPOINT ["java", "-XX:+EnableDynamicAgentLoading", "-jar", "app.jar"]
