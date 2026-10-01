# Multi-stage is unnecessary here because CI builds jars first; this image
# just runs the prebuilt Spring Boot jar. Build: mvn clean package -pl <module>
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as non-root user (container security basic)
RUN addgroup -S medicore && adduser -S medicore -G medicore
USER medicore

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
