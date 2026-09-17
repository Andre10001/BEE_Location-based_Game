FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package org.springframework.boot:spring-boot-maven-plugin:3.2.5:repackage

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/camunda-springboot-app-1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]