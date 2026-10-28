# Family Fleet

Családi járműnyilvántartó REST API: háztartások, családtagok, járművek, biztosítások és
határidők (műszaki vizsga, KGFB-évforduló, gépjárműadó, autópálya-matrica stb.) egy helyen.

## Követelmények

- JDK 21 (pl. Eclipse Temurin 21)
- Docker Desktop (vagy más Docker-futtatókörnyezet) – az alkalmazás és a tesztek is
  konténerben futó PostgreSQL-t használnak
- A Maven telepítése nem szükséges, a projekt Maven Wrappert (`mvnw`) használ

## Indítás

Az alkalmazás indításakor a Spring Boot Docker Compose támogatása automatikusan elindítja a
`compose.yaml`-ben leírt PostgreSQL-konténert, a Flyway pedig lefuttatja a migrációkat.

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

Az API alapértelmezetten a `http://localhost:8080` címen érhető el.

Kipróbálható kérések az [`http/`](http) mappában vannak (IntelliJ HTTP Client formátum, a
VS Code REST Client bővítménye is nagyrészt érti). A hibák egységesen RFC 9457
`application/problem+json` formában érkeznek.

## Tesztek

A tesztek Testcontainers segítségével saját, eldobható PostgreSQL-konténert indítanak,
ezért futniuk kell a Dockernek.

```bash
./mvnw test
```

A teljes ellenőrzés (tesztek + csomagolás), ugyanaz, ami a PR-ok előtt is fut:

```bash
./mvnw verify
```

Windows alatt a `./mvnw` helyett `.\mvnw.cmd` kell.

## Technológiák

- Java 21, Spring Boot 4.1
- Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation
- PostgreSQL 18, Flyway (sémamigrációk)
- Lombok
- Tesztek: JUnit 5, AssertJ, Mockito, Testcontainers 2

## Projektfelépítés

```text
src/main/java/hu/familyfleet
├── common/      közös osztályok (pl. AuditableEntity)
├── household/   háztartás és családtagok
├── vehicle/     járművek
├── insurance/   biztosítók és kötvények
└── deadline/    határidők
src/main/resources/db/migration   Flyway-migrációk (V1, V2, ...)
```

A csomagolás funkció (feature) szerinti: minden csomag a saját entitásait, repository-jait és
később a service- és controller-osztályait tartalmazza.

## Dokumentáció

- [Architektúra-döntések (ADR)](docs/adr) – miért döntöttünk úgy, ahogy

## Munkafolyamat

Minden változás GitHub Issue-ból indul, saját branchen készül (`feature/<issue>-rovid-nev`),
Conventional Commits formátumú commitokkal (`feat(#12): ...`), és squash merge-dzsel kerül a
`main`-re, miután a `./mvnw verify` zöld.
