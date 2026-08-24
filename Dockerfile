# Multi-stage build: compile with Maven + a full JDK, run on a slim JRE so
# the final image doesn't carry build tools around.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cache dependencies in their own layer — only re-downloads when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:25-jre AS run
WORKDIR /app

# Don't run as root inside the container.
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /workspace/target/t-coop-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
