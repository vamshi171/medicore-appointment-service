# Build jar first: mvn clean package
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S medicore && adduser -S medicore -G medicore
USER medicore

COPY target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
