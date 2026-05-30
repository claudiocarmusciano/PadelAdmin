# ─────────────────────────────────────────────────────────────────────────────
# PadelAdmin — imagen única: frontend (React/Vite) bundleado dentro del
# backend (Spring Boot). Pensada para Railway / cualquier PaaS con Docker.
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: build del frontend ──────────────────────────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# Build de producción → /app/dist (axios cae a "/api", mismo origen)
RUN npm run build

# ── Stage 2: build del backend (jar con el frontend embebido) ─────────────────
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
# Cache de dependencias: copiar pom primero
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
# Código fuente del backend
COPY backend/src ./src
# Embeber el build del frontend como recursos estáticos de Spring
COPY --from=frontend /app/dist/ ./src/main/resources/static/
RUN mvn -q -B clean package -DskipTests

# ── Stage 3: runtime liviano ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# Railway inyecta PORT; Spring lo lee via server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
