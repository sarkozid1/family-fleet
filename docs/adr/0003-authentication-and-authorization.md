# ADR-0003: Hitelesítés JWT-vel, háztartásszintű jogosultság 404-gyel

## Státusz
Elfogadva

## Kontextus
Az API eddig bárki számára elérhető volt, és mindenki minden háztartást látott. A
felhasználóknak be kell jelentkezniük, és csak a saját háztartásuk adatait érhetik el. Két
kérdésben kellett dönteni:
1. Session alapú hitelesítés (cookie + szerveroldali session) vagy token (JWT)?
2. Idegen háztartás adatainál 403 Forbidden vagy 404 Not Found legyen a válasz?

## Döntés

### 1. JWT, a Spring Security OAuth2 Resource Server támogatásával, saját (HS256) kulccsal
- `POST /api/auth/login`: e-mail + jelszó → aláírt JWT. A token tartalma:
  - `sub` = felhasználó-azonosító
  - `email`
  - `roles`
  - `iss`, `iat`, `exp` (alapból 1 óra)
- Minden további kérés `Authorization: Bearer <token>` fejléccel jön.
- Az ellenőrzést a Spring Security beépített `BearerTokenAuthenticationFilter`-e végzi; saját
  szűrőt nem írunk.
- A kulcs egy legalább 32 bájtos titok (`app.security.jwt.secret`). Élesben környezeti
  változóból jön (`JWT_SECRET`).
- A jelszavak `DelegatingPasswordEncoder`-rel (alapból BCrypt) tárolódnak, `{bcrypt}` előtaggal.

**Miért egyszerűbb itt, mint a session?**
- Az API állapotmentes, nincs böngészős frontend. A klienseknek (HTTP-fájlok, Swagger UI,
  mobil) egy fejlécet kell küldeniük, nem cookie-t kezelni.
- Mivel a hitelesítés nem cookie-ban utazik, a böngésző nem küldi automatikusan. Így CSRF-védelem
  nem kell, és session-tárolás sem (nincs sticky session, nincs Redis több példánynál).
- Tesztben a token egy fejléc. A Spring Security tesztsegédei (`jwt()`, `@WithMockUser`)
  közvetlenül támogatják.
- Saját JWT-szűrő írása helyett a keretrendszer bevált Resource Server komponensét használjuk.
  Csak a kibocsátás (`JwtEncoder`) a miénk.

**Miért szimmetrikus (HS256) kulcs, és nem RSA?** A tokent ugyanaz az alkalmazás bocsátja ki és
ellenőrzi, így nincs harmadik fél, akinek csak ellenőriznie kellene. Ha később külön auth
szerver (pl. Keycloak) jön, RSA/JWK-ra váltunk, és a Resource Server oldal alig változik.

### 2. Idegen háztartás: 404 Not Found
Ha a felhasználó olyan háztartáshoz (vagy annak járművéhez, kötvényéhez, határidejéhez) fér
hozzá, ami nem az övé, ugyanazt a 404-et kapja, mint egy nem létező azonosítónál.
- **Indoklás:** a 403 elárulná, hogy az azonosító létezik (felsorolással fel lehetne térképezni
  a többi családot). A 404 nem ad ki információt, és a GitHub is így kezeli a privát repókat.
- A kérés törzsében hivatkozott idegen elem (pl. `vehicleId`) ugyanúgy kezelődik, mint a nem
  létező: ugyanaz a 400-as „nem létező …” üzenet.
- **403** csak szerepkör-hiánynál jön (pl. biztosító felvétele `USER` szerepkörrel). Ott az
  erőforrás nyilvános, csak a művelet tiltott.

### Hol ellenőrzünk?
- **URL-szint (`SecurityConfig`):** nyilvános végpontok (regisztráció, login, health, info,
  Swagger), minden más hitelesített; `POST /api/insurers` csak `ADMIN`.
- **Adatszint (service):** a `HouseholdAccessGuard` minden háztartáshoz kötött műveletnél
  ellenőrzi a `household_access` táblát. A service-ben, mert ott van meg a betöltött entitás és
  a háztartása, és így minden belépési pont (controller, későbbi job) ugyanazt a szabályt kapja.
- A napi emlékeztető-job rendszerszintű, felhasználó nélkül fut; nem érinti.

## Következmények
- **Előny:** állapotmentes, skálázható, egyszerűen tesztelhető; egységes ProblemDetail a 401-re
  és a 403-ra is.
- **Előny:** idegen adatok létezése nem szivárog ki.
- **Hátrány:** a JWT a lejáratáig érvényes, kijelentkezéskor nem vonható vissza (nincs
  tiltólista). A rövid (1 órás) élettartam ezt mérsékli; refresh token nincs.
- **Hátrány:** a regisztrációnál a 409 („foglalt e-mail”) elárulja, hogy egy cím regisztrálva
  van. Ezt a kényelmes hibaüzenetért vállaljuk; e-mailes megerősítéssel lehetne kiváltani.
- **Hátrány:** háztartás megosztására (másik felhasználó meghívására) még nincs végpont; a
  `household_access` tábla erre fel van készítve.
- **Hátrány:** admin szerepkört csak adatbázisból lehet adni
  (`UPDATE app_user SET role = 'ADMIN' WHERE email = ...`).
- **Hátrány:** nincs bejelentkezési próbálkozás-korlátozás (rate limiting, fiókzárolás).
