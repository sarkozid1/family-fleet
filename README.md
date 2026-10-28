# Family Fleet

[![CI](https://github.com/sarkozid1/family-fleet/actions/workflows/ci.yml/badge.svg)](https://github.com/sarkozid1/family-fleet/actions/workflows/ci.yml)

Családi járműnyilvántartó REST API: háztartások, családtagok, járművek, biztosítások és
határidők (műszaki vizsga, KGFB-évforduló, gépjárműadó, autópálya-matrica stb.) egy helyen.

## Követelmények

- JDK 21 (pl. Eclipse Temurin 21)
- Docker Desktop (vagy más Docker-futtatókörnyezet) – az alkalmazás és a tesztek is
  konténerben futó PostgreSQL-t használnak
- A Maven telepítése nem szükséges, a projekt Maven Wrappert (`mvnw`) használ
- A Node.js telepítése sem szükséges: a build a frontendhez a projektbe (`frontend/node`) tölti le

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

Indulás után a **webes felület a `http://localhost:8080` címen** érhető el (regisztráció,
háztartások, tagok, járművek, kötvények, határidők), az API pedig a `http://localhost:8080/api`
alatt. A Maven a React-felületet is felépíti; az első indítás ezért egy percig is eltarthat.

A compose egy Mailpit fejlesztői SMTP-szervert is indít: a napi emlékeztető-levelek
(minden reggel 7-kor, 30/7/1 nappal az esedékesség előtt) a `http://localhost:8025`
webes felületen nézhetők meg, valódi címzetthez nem jutnak el.

Kipróbálható kérések az [`http/`](http) mappában vannak (IntelliJ HTTP Client formátum, a
VS Code REST Client bővítménye is nagyrészt érti). A hibák egységesen RFC 9457
`application/problem+json` formában érkeznek, benne a `requestId`-vel, amely a válasz
`X-Request-Id` fejlécében és minden hozzá tartozó logsorban is szerepel.

### Hitelesítés

Az `/api/**` végpontok (a regisztráció és a bejelentkezés kivételével) JWT-t várnak:

1. `POST /api/auth/register` – `{ "email", "password" (8–72 karakter), "displayName" }`
2. `POST /api/auth/login` – a válasz `accessToken` mezője egy 1 óráig érvényes token
3. minden további kérés: `Authorization: Bearer <accessToken>`

Mindenki csak a saját háztartásait látja; idegen háztartás adatai 404-et adnak (lásd
[ADR-0003](docs/adr/0003-authentication-and-authorization.md)). Biztosítót felvenni csak
`ADMIN` szerepkörrel lehet, ezt adatbázisból lehet megadni
(`UPDATE app_user SET role = 'ADMIN' WHERE email = '...'`). Az [`http/auth.http`](http/auth.http)
fájlt kell elsőként futtatni, az eltárolja a tokent a többi kéréshez. A Swagger UI-ban az
**Authorize** gombbal adható meg a token.

| Cím | Mi ez |
|---|---|
| `http://localhost:8080` | Webes felület (React) |
| `http://localhost:8080/swagger-ui.html` | Swagger UI, interaktív API-dokumentáció |
| `http://localhost:8080/v3/api-docs` | OpenAPI-leírás (JSON) |
| `http://localhost:8080/actuator/health` | Állapot (plusz `/liveness` és `/readiness`) |
| `http://localhost:8080/actuator/info` | Verzió, build ideje, Java-verzió |
| `http://localhost:8025` | Mailpit: az elküldött levelek |

## Frontend fejlesztése

A felület a [`frontend/`](frontend) mappában van: React 19, TypeScript, Vite, React Router, a
tesztek Vitesttel és Testing Libraryvel futnak. A `./mvnw` build a frontendet is lefordítja
(típusellenőrzéssel), teszteli, és a `target/classes/static` mappába teszi; onnan a Spring Boot
szolgálja ki.

Gyors, automatikusan frissülő fejlesztéshez indítsd el a backendet
(`.\mvnw.cmd spring-boot:run`), majd egy második terminálban a Vite dev szervert, és nyisd meg a
`http://localhost:5173` címet. A Vite az `/api` kéréseket a 8080-as backendre továbbítja.

```powershell
cd frontend; .\node\npm.cmd run dev
```

(A `frontend\node` mappa az első Maven-build után jön létre. Ha a Node.js telepítve van a gépen,
a sima `npm run dev` is működik.) Csak frontend-tesztek: `.\node\npm.cmd test`. Ha csak a
backenden dolgozol, a frontend buildje kihagyható: `.\mvnw.cmd spring-boot:run "-Dskip.npm"`.

## Profilok és konfiguráció

| Profil | Mikor | Jellemzők |
|---|---|---|
| `dev` | alapértelmezett, ha nincs megadva profil | Docker Compose indítja a Postgrest és a Mailpitet, SQL-logolás, több Actuator-végpont |
| `prod` | élesben: `SPRING_PROFILES_ACTIVE=prod` | JSON (ECS) logok, csak `health` és `info`, Swagger alapból kikapcsolva, minden titok környezeti változóból |

A `prod` profil kötelező környezeti változói (alapérték nélkül, hiányukban az alkalmazás nem indul):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_HOST`, `REMINDER_FROM`, `JWT_SECRET` (legalább 32 karakter).
Opcionálisak: `JWT_TTL` (PT1H), `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`,
`MAIL_SMTP_STARTTLS`, `REMINDER_ENABLED`, `REMINDER_CRON`, `API_DOCS_ENABLED` (false).
A teljes lista az [`application-prod.yml`](src/main/resources/application-prod.yml) fejlécében.

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

A `verify` a teszteken túl minőségi kapukat is ellenőriz:

- **Lefedettség (JaCoCo):** legalább 80% sorlefedettség, a riport: `target/site/jacoco/index.html`
- **Formázás (Spotless, Palantir Java Format):** eltérés esetén a build elbukik; javítás:

```bash
./mvnw spotless:apply
```

Ugyanez fut a GitHub Actions CI-ban minden PR-ra és a `main`-re, és ott a Docker-image buildje is
ellenőrizve van.

## Docker

Multi-stage build (JDK-val fordít, csak JRE-vel fut), nem root felhasználóval, `prod` profillal:

```bash
docker build -t family-fleet .
```

```bash
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://db:5432/familyfleet -e DB_USERNAME=familyfleet -e DB_PASSWORD=... -e MAIL_HOST=smtp.example.com -e REMINDER_FROM=noreply@example.com -e JWT_SECRET=... family-fleet
```

## Technológiák

- Java 21, Spring Boot 4.1
- Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation
- Spring Security: OAuth2 Resource Server (JWT, HS256), BCrypt
- PostgreSQL 18, Flyway (sémamigrációk)
- Spring Mail + ütemezett feladat (`@Scheduled`), Mailpit fejlesztői SMTP
- Spring Boot Actuator, strukturált (ECS) logolás, springdoc-openapi (Swagger UI)
- Lombok
- Tesztek: JUnit 5, AssertJ, Mockito, Testcontainers 2
- Frontend: React 19, TypeScript, Vite, React Router; Vitest + Testing Library
- Minőség és szállítás: JaCoCo, Spotless (Palantir Java Format), GitHub Actions, Docker

## Projektfelépítés

```text
src/main/java/hu/familyfleet
├── common/      közös: hibakezelés, lapozás, Clock, kérésazonosító, OpenAPI
├── household/   háztartás és családtagok
├── vehicle/     járművek
├── insurance/   biztosítók és kötvények
├── deadline/    határidők, generálási és ismétlődési szabályok
├── reminder/    napi emlékeztető e-mail
└── security/    felhasználók, regisztráció, bejelentkezés, JWT, biztonsági szabályok
src/main/resources/db/migration   Flyway-migrációk (V1, V2, ...)
frontend/                         React + TypeScript webes felület (Vite)
```

A csomagolás funkció (feature) szerinti: minden csomag a saját entitásait, repository-jait,
service- és controller-osztályait tartalmazza.

## Dokumentáció

- [Architektúra-áttekintés](docs/architecture.md) – rétegek, adatmodell (ER-diagram), egy kérés útja
- [Architektúra-döntések (ADR)](docs/adr) – miért döntöttünk úgy, ahogy

## Munkafolyamat

Minden változás GitHub Issue-ból indul, saját branchen készül (`feature/<issue>-rovid-nev`),
Conventional Commits formátumú commitokkal (`feat(#12): ...`), és squash merge-dzsel kerül a
`main`-re, miután a `./mvnw verify` zöld.
