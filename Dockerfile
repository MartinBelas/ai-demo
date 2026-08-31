FROM node:24-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src/ src/
COPY --from=frontend /workspace/frontend/dist/ frontend/dist/
RUN mvn -B -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/target/ai-demo-1.0-SNAPSHOT.jar /app/ai-demo.jar
COPY --from=build /workspace/target/lib/ /app/lib/
USER app
ENV APP_INTERFACE=http \
    PORT=8080 \
    OLLAMA_ENABLED=false
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/ai-demo.jar"]
