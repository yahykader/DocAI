# DocAI Backend — Multi-Stage Dockerfile
#
# Build context: ./backend  (run: docker build -t docai-backend:test ./backend)
#
# Stage breakdown:
#   dependencies  — downloads all Maven deps; layer cached until any POM changes
#   build         — compiles sources, packages fat JAR, extracts Spring Boot layers
#   runtime       — minimal JRE image, non-root user docai (UID/GID 1001)
#
# Target size: < 300 MB (JRE Alpine + layered JARs only)
# Management port: 9091 (actuator) — internal, not exposed
# API port: 8080 — exposed

# ── Stage 1: Dependency cache ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS dependencies

# curl + unzip required by ./mvnw to download and unpack Maven 3.9.9 on first run
RUN apk add --no-cache curl unzip

WORKDIR /build

# Maven wrapper — invalidates cache only when wrapper config changes
COPY .mvn/            .mvn/
COPY mvnw             mvnw
RUN chmod +x mvnw

# Parent POM + all 12 module POMs — source code excluded for maximum cache reuse
COPY pom.xml                             pom.xml
COPY docai-domain/pom.xml                docai-domain/pom.xml
COPY docai-application/pom.xml           docai-application/pom.xml
COPY docai-commons/pom.xml               docai-commons/pom.xml
COPY docai-adapter-in-rest/pom.xml       docai-adapter-in-rest/pom.xml
COPY docai-adapter-in-kafka/pom.xml      docai-adapter-in-kafka/pom.xml
COPY docai-adapter-out-mongodb/pom.xml   docai-adapter-out-mongodb/pom.xml
COPY docai-adapter-out-kafka/pom.xml     docai-adapter-out-kafka/pom.xml
COPY docai-adapter-out-valkey/pom.xml    docai-adapter-out-valkey/pom.xml
COPY docai-adapter-out-ai/pom.xml        docai-adapter-out-ai/pom.xml
COPY docai-adapter-out-storage/pom.xml   docai-adapter-out-storage/pom.xml
COPY docai-adapter-out-external/pom.xml  docai-adapter-out-external/pom.xml
COPY docai-bootstrap/pom.xml             docai-bootstrap/pom.xml

# Pre-download all runtime + plugin dependencies offline
# This layer is re-used on every source-only change
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# ── Stage 2: Build ─────────────────────────────────────────────────────────
FROM dependencies AS build

# Copy all module sources (dependency cache from stage 1 is reused)
COPY . .

# Compile and package — tests are skipped here; CI runs them separately
RUN ./mvnw clean package -DskipTests --no-transfer-progress

# Extract Spring Boot layertools structure:
#   dependencies/          — 3rd-party JARs (changes rarely)
#   snapshot-dependencies/ — SNAPSHOT JARs (changes occasionally)
#   spring-boot-loader/    — Spring Boot loader classes (changes rarely)
#   application/           — application classes + resources (changes often)
# Ordering the COPY in runtime stage from least to most frequently changed
# maximises Docker layer cache hits on application-only rebuilds.
RUN java -Djarmode=layertools \
    -jar docai-bootstrap/target/docai-bootstrap-*.jar \
    extract --destination /build/extracted

# ── Stage 3: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root system user with explicit UID 1001
# Matches K8s securityContext.runAsUser: 1001 in k8s/deployment.yaml (SEC-001)
RUN addgroup -S -g 1001 docai \
 && adduser  -S -u 1001 -G docai -h /home/docai -s /sbin/nologin docai

# Layered JARs — ordered from least to most frequently changed for cache efficiency
COPY --from=build --chown=docai:docai /build/extracted/dependencies/          ./
COPY --from=build --chown=docai:docai /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=docai:docai /build/extracted/spring-boot-loader/    ./
COPY --from=build --chown=docai:docai /build/extracted/application/           ./

USER docai

# Expose API port. Management port 9091 is intentionally NOT exposed:
# internal only, scraped by Prometheus within the cluster (SEC-002).
EXPOSE 8080

# Health check targets the dedicated management port (application.yml: management.server.port=9091)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:9091/actuator/health || exit 1

# JarLauncher is required when using Spring Boot layertools extraction
# (the application is not a standard executable JAR at this point)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
