FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY config config
COPY src src

RUN chmod +x ./gradlew \
    && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system bankflow \
    && useradd --system --gid bankflow --home-dir /app --shell /usr/sbin/nologin bankflow

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

USER bankflow

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
