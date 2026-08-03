# Build stage: uses the repo's own Maven wrapper so the build matches
# local development exactly, no separate Maven version to keep in sync.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy wrapper and POM first so dependency resolution is cached in its own
# Docker layer, only re-run when pom.xml actually changes.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -q dependency:go-offline

COPY src src
# Tests need a real database to boot the Spring context, which doesn't
# exist during an isolated image build. Real test execution belongs in CI
# (Day 5), not here.
RUN ./mvnw -q package -DskipTests

# Runtime stage: JRE only, no build tools shipped in the final image.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
