# syntax=docker/dockerfile:1
# =========================================================
# 1. fázis: build (JDK + Maven Wrapper). A tesztek a CI-ban futnak, nem az image buildjében:
#    az image-build gyors és Docker-in-Docker nélküli marad (a tesztekhez Testcontainers kellene).
# =========================================================
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /workspace

# Előbb csak a build-leírók: amíg a pom.xml nem változik, ez a réteg a cache-ből jön.
COPY mvnw pom.xml lombok.config ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src src
# A Maven-repó BuildKit cache-ben marad a buildek között, nem kerül bele az image-be.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true

# A Spring Boot "réteges" kibontása: függőségek, loader, saját kód külön rétegben.
# Kódváltozáskor csak a kicsi "application" réteg változik, a nagy függőség-réteg cache-ből jön.
RUN java -Djarmode=tools -jar target/family-fleet-*.jar extract --layers --launcher --destination extracted

# =========================================================
# 2. fázis: futtatás (csak JRE, se JDK, se Maven, se forráskód)
# =========================================================
FROM eclipse-temurin:21.0.12_8-jre-noble
WORKDIR /app

# Nem root felhasználó: ha az alkalmazást feltörik, a támadó nem root a konténerben.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring:spring

# Éles profil; a titkokat (DB_URL, JWT_SECRET, ...) futtatáskor környezeti változóként kell átadni.
# A JVM a konténer memóriakorlátjához igazodik.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
