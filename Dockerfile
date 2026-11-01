# Multi-stage build: the JDK + Maven toolchain stays in the build stage; the final image only has a JRE and the jar.

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# The cache mount keeps ~/.m2 between builds without baking it into a layer.
# Tests are skipped here because CI already ran them in `./mvnw verify`; this stage only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine

# Run as an unprivileged user: if the app is ever compromised, the attacker isn't root in the container
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/foliotrack-*.jar app.jar
USER app

EXPOSE 8080
# Size the heap from the container's memory limit, not the host's
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
