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
# Imagen base de Monitoring: JRE 21 + agente de New Relic (Debian)
FROM ghcr.io/tp1-2-ingsis/monitoring-config:latest
WORKDIR /app

# Usuario no-root por seguridad (la imagen base es Debian, no Alpine)
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
# New Relic: NEW_RELIC_LICENSE_KEY se inyecta en runtime; el nombre es propio de este servicio.
ENV NEW_RELIC_APP_NAME=snippets

ENTRYPOINT ["java", "-jar", "app.jar"]
