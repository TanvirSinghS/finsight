# Build context is the repository root so Maven can package ../db/migration into the jar.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY backend-java/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline -DskipTests

COPY backend-java/src ./src
COPY db /db
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /build/target/finsight-api-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
