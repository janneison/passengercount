# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache de dependencias
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Compilar
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuario no-root
RUN addgroup -S app && adduser -S app -G app

# (Opcional) zona horaria/puerto; sobreescríbelo en runtime
ENV TZ=America/Bogota \
    SERVER_PORT=8080 \
    JAVA_OPTS=""

# Jar compilado
COPY --from=build /app/target/*.jar /app/app.jar

# Healthcheck
RUN apk add --no-cache curl
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/health" || exit 1

USER app
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT}"]
