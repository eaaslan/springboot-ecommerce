# Generic backend Dockerfile — one image template, swap MODULE per service.
#
# Stage 1 builds the entire Maven workspace once (heavy, but runs only on the
# first service in a `docker compose up --build` and the layer is reused for
# the other 12 services thanks to BuildKit cache).
#
# Stage 2 copies just the chosen module's fat JAR into a slim JRE base.
#
# Build with:
#   docker build --build-arg MODULE=services/user-service -t ecommerce-user-service .

# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies — copy only the POMs first, resolve, then copy sources.
# Re-running with code changes hits this cache hot when the dependency graph
# is unchanged.
COPY pom.xml .
COPY shared/common/pom.xml shared/common/
COPY infrastructure/config-server/pom.xml infrastructure/config-server/
COPY infrastructure/discovery-server/pom.xml infrastructure/discovery-server/
COPY infrastructure/api-gateway/pom.xml infrastructure/api-gateway/
COPY services/user-service/pom.xml services/user-service/
COPY services/product-service/pom.xml services/product-service/
COPY services/cart-service/pom.xml services/cart-service/
COPY services/inventory-service/pom.xml services/inventory-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/order-service/pom.xml services/order-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/recommendation-service/pom.xml services/recommendation-service/
COPY services/catalog-stream-service/pom.xml services/catalog-stream-service/
COPY services/seller-service/pom.xml services/seller-service/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline -fail-at-end || true

# Now copy actual sources and build everything in one shot.
COPY shared shared
COPY infrastructure infrastructure
COPY services services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -Dspotless.check.skip=true package

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# Drop privileges (1000 is sometimes already taken on debian-slim, pick something free)
RUN useradd --system --user-group --uid 2000 spring
USER spring
WORKDIR /app

ARG MODULE
COPY --from=build --chown=spring:spring /workspace/${MODULE}/target/*.jar app.jar

# Sensible JVM flags for containers; can be overridden by JAVA_TOOL_OPTIONS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
