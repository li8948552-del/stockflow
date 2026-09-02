FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
