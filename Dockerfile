# ---------- Stage 1: build the frontend ----------
FROM node:24-alpine AS ui-build
WORKDIR /ui
RUN npm install -g pnpm@10
COPY src/main/ui/package.json src/main/ui/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY src/main/ui/ ./
RUN pnpm build

# ---------- Stage 2: build the backend (frontend baked in as static resources) ----------
FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN ./mvnw -q dependency:go-offline
COPY src/main/java/ src/main/java/
COPY src/main/resources/ src/main/resources/
COPY --from=ui-build /ui/dist/ src/main/resources/static/
RUN ./mvnw -q package -DskipTests

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 yarnia \
    && mkdir /data && chown yarnia:yarnia /data
USER yarnia
WORKDIR /app
COPY --from=backend-build /app/target/yarnia-*-SNAPSHOT.jar app.jar
# SQLite database lives on a volume so games survive container recreation
ENV YARNIA_DB_PATH=/data/yarnia.db
VOLUME /data
EXPOSE 3001
ENTRYPOINT ["java", "-jar", "app.jar"]
