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
# SerialGC and a disabled JMX registry shave startup time in a small free-tier
# container, where the first request after a cold boot is what users feel.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=75", "-Dspring.jmx.enabled=false", \
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
