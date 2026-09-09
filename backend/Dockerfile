# ==========================================
# Stage 1: Build JAR with Maven & Eclipse Temurin JDK 21
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# 1. Cache Maven dependencies
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# 2. Compile and package application
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ==========================================
# Stage 2: Minimal JRE Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy repackaged JAR from build stage
COPY --from=builder /build/target/core_banking-0.0.1-SNAPSHOT.jar app.jar

# Dynamic server port
ENV PORT=8080
EXPOSE 8080

# Container healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]