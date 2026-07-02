# Stage 1: Build the shadow JAR
FROM ghcr.io/graalvm/native-image-community:25-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Runtime on JVM (BC cipher works correctly, GraalVM native image issues bypassed)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /home/app
EXPOSE 8080
# Copy the JAR - wildcard since shadow plugin may vary the exact output path
COPY --from=builder /workspace/build/libs/*.jar /home/app/jilalibff.jar
ENTRYPOINT ["java", "-jar", "/home/app/jilalibff.jar"]
