# ==========================================
# 1. Build Stage (Gradle + JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar configuración de Gradle primero para cachear dependencias
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copiar el código fuente y empaquetar el JAR ejecutable
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# 2. Runtime Stage (JRE 21 liviano)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
