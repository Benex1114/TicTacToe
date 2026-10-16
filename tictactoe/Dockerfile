# ---- Build stage: compile the app into a runnable jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies first so Docker can cache them between builds
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# ---- Run stage: only a Java runtime and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# JVM settings for a small, slow free-plan server:
#   MaxRAMPercentage=75   keep memory within the 512 MB limit
#   TieredStopAtLevel=1   compile code quickly instead of optimising it, so startup is faster
#   UseSerialGC           the simplest garbage collector; least CPU and memory overhead on one core
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-jar", "app.jar"]
