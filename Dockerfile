# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Cache Maven dependencies before copying source
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline

# Copy backend source and frontend (bundled into the WAR as static files)
COPY src ./src
COPY frontend/ ./src/main/webapp/

RUN ./mvnw -q package -DskipTests

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
# Tomcat 10.1 = Jakarta Servlet 6.0 (matches pom.xml)
# pipefix.jar is NOT needed — that workaround is Windows-only (NIO AF_UNIX bug)
FROM tomcat:10.1-jre17-temurin-alpine
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/sasoori-backend-1.0.0-SNAPSHOT.war \
                  /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]
