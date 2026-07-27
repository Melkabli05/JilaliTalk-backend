# Stage 1: Build the shadow JAR
FROM ghcr.io/graalvm/native-image-community:25-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Runtime on JVM (BC cipher works correctly, GraalVM native image issues bypassed)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /home/app
EXPOSE 8080
# Create the SQLite parent directory in the image so the BFF's DataSource can
# initialize on first boot. DbDirectoryInitializer also runs at startup and is
# idempotent (Files.createDirectories ignores an existing dir), but doing it
# here means the dir is present even if a future refactor accidentally bypasses
# the initializer. /home/app/ is the WORKDIR, and is already writable.
RUN mkdir -p /home/app/data && chown -R 1000:1000 /home/app
# Copy the JAR - wildcard since shadow plugin may vary the exact output path
COPY --from=builder /workspace/build/libs/*.jar /home/app/jilalibff.jar
ENTRYPOINT ["java", "--enable-preview", "-jar", "/home/app/jilalibff.jar"]
