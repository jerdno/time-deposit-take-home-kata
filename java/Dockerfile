# syntax=docker/dockerfile:1

# Build stage: the Maven wrapper and a JDK, so building the image needs nothing but Docker.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# The cache mount keeps the downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package \
    && cp target/time-deposit-kata-*.jar app.jar

# Runtime stage: a JRE and the application jar only.
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
