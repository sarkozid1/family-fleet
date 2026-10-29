# syntax=docker/dockerfile:1
# =========================================================
# 1. fázis: build (JDK + Maven Wrapper). A tesztek a CI-ban futnak, nem az image buildjében:
#    az image-build gyors és Docker-in-Docker nélküli marad (a tesztekhez Testcontainers kellene).
# =========================================================
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /workspace

# Előbb csak a függőségleírók (pom.xml, package-lock.json): amíg ezek nem változnak, a letöltött
# Maven-függőségek, a Node és az npm-cache rétege a Docker-cache-ből jön. Szándékosan nem
# BuildKit cache-mount: azt nem minden builder támogatja ugyanígy (pl. a Railway sajátos
# azonosító-formátumot vár), a rétegcache viszont mindenhol működik.
COPY mvnw pom.xml lombok.config ./
COPY .mvn .mvn
COPY frontend/package.json frontend/package-lock.json frontend/
RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline \
    && ./mvnw -B -ntp frontend:install-node-and-npm@install-node-and-npm frontend:npm@npm-ci

COPY frontend frontend
COPY src src
# A Maven a frontend-maven-pluginnal a React-felületet is felépíti (target/classes/static).
RUN ./mvnw -B -ntp package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true

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
# A JVM a konténer memóriakorlátjához igazodik. Felhasználás szerint számlázó platformon (Railway)
# érdemes felülírni egy fix felső korláttal, lásd docs/deploy-railway.md.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
