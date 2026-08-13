# ---- build stage: Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/maven-settings.xml /root/.m2/settings.xml
COPY backend/pom.xml pom.xml
RUN mvn -q -DskipTests dependency:go-offline || true
COPY backend/src src
RUN mvn -q -DskipTests package

# ---- runtime stage: JRE 17 ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /build/target/backend-1.0-SNAPSHOT.jar app.jar
COPY backend/data/seniors /app/data/seniors
COPY preproducts/metaskills/community-experience-distiller /app/preproducts/metaskills/community-experience-distiller
ENV SKILLHUB_SEED_COMMUNITY=true
ENV LLM_PROVIDER=mock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
