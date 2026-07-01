FROM ghcr.io/graalvm/native-image-community:25-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew buildNativeLayersTask dockerPrepareContext --no-daemon

FROM ghcr.io/graalvm/native-image-community:25-ol9 AS graalvm
WORKDIR /home/app
COPY --link --from=builder /workspace/build/docker/native-main/layers/libs /home/app/libs
COPY --link --from=builder /workspace/build/docker/native-main/layers/app /home/app/
COPY --link --from=builder /workspace/build/docker/native-main/layers/resources /home/app/resources
RUN mkdir /home/app/config-dirs
RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
RUN mkdir -p /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
RUN mkdir -p /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
RUN mkdir -p /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
RUN mkdir -p /home/app/config-dirs/com.zaxxer/HikariCP/5.0.1
RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
RUN mkdir -p /home/app/config-dirs/com.h2database/h2/2.1.210
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/io.netty/netty-common/4.1.115.Final /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/io.netty/netty-transport/4.1.115.Final /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2 /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/com.zaxxer/HikariCP/5.0.1 /home/app/config-dirs/com.zaxxer/HikariCP/5.0.1
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/ch.qos.logback/logback-classic/1.4.9 /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
COPY --link --from=builder /workspace/build/docker/native-main/config-dirs/com.h2database/h2/2.1.210 /home/app/config-dirs/com.h2database/h2/2.1.210
RUN native-image --exclude-config .*/libs/netty-buffer-4.2.9.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.2.9.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.2.9.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-transport-4.2.9.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.2.9.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.2.9.Final.jar ^/META-INF/native-image/.* -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -o application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile,/home/app/config-dirs/io.netty/netty-buffer/4.1.80.Final,/home/app/config-dirs/io.netty/netty-common/4.1.115.Final,/home/app/config-dirs/io.netty/netty-codec-http/4.1.80.Final,/home/app/config-dirs/io.netty/netty-transport/4.1.115.Final,/home/app/config-dirs/io.netty/netty-handler/4.1.80.Final,/home/app/config-dirs/io.netty/netty-codec-http2/4.1.80.Final,/home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2,/home/app/config-dirs/com.zaxxer/HikariCP/5.0.1,/home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9,/home/app/config-dirs/com.h2database/h2/2.1.210 com.jilali.Application
FROM cgr.dev/chainguard/wolfi-base:latest
EXPOSE 8080
COPY --link --from=graalvm /home/app/application /app/application
ENTRYPOINT ["/app/application"]
