# Telepítés Railway-re

A [Railway](https://railway.com) a GitHub-repóból a meglévő `Dockerfile` alapján buildeli és
futtatja az alkalmazást, mellé kezelt PostgreSQL-t és HTTPS-es címet ad. A beállítások egy része a
repóban van ([`railway.json`](../railway.json): Dockerfile-build, health check, újraindítás), a
többit (adatbázis, környezeti változók, domain) a Railway felületén kell megadni.

> A Railway felülete időnként változik; ha egy menüpont máshol van, a Railway dokumentációjában
> ugyanezekkel a kulcsszavakkal (Variables, Networking, Deploy, Backups) megtalálható.

## Előfeltételek

- Railway-fiók fizetős (Hobby) csomaggal; a tartósan futó szolgáltatáshoz ez kell
- A GitHub-fiók összekötve a Railway-jel (hozzáférés a `family-fleet` repóhoz)
- Egy e-mail-küldő szolgáltatás SMTP-adatai (pl. Brevo, vagy Gmail alkalmazásjelszóval) – lásd
  [E-mail](#e-mail) lent

## 1. Projekt és alkalmazás

1. **New Project → Deploy from GitHub repo →** `family-fleet`.
2. A Railway megtalálja a `railway.json`-t és a `Dockerfile`-t, és elindítja az első buildet. Ez
   még elbukhat, mert nincsenek beállítva a környezeti változók – ez rendben van.

## 2. Adatbázis

1. A projektben: **New → Database → PostgreSQL**.
2. A szolgáltatás neve maradjon **`Postgres`** (a lenti változók erre a névre hivatkoznak).

A Flyway az első indításkor létrehozza a teljes sémát (a `btree_gist` bővítményt is).

## 3. Környezeti változók

Az alkalmazás szolgáltatásán: **Variables → Raw Editor**, és illeszd be (a `<...>` részeket
cseréld le):

```text
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<legalább 32 karakteres véletlen szöveg>
REMINDER_FROM=Family Fleet <noreply@sajat-domain.hu>
MAIL_HOST=<smtp szerver>
MAIL_PORT=587
MAIL_USERNAME=<smtp felhasználó>
MAIL_PASSWORD=<smtp jelszó>
JAVA_TOOL_OPTIONS=-Xmx384m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError
REGISTRATION_ALLOWED_EMAILS=<a te e-mail-címed>
```

- A `${{Postgres.…}}` hivatkozásokat a Railway tölti ki az adatbázis adataival; a jelszót nem
  kell kimásolni sehova.
- A `PORT`-ot nem kell megadni: a Railway adja, és az alkalmazás azon figyel.
- A `SPRING_PROFILES_ACTIVE=prod` a Dockerfile-ban már be van állítva.
- **`REGISTRATION_ALLOWED_EMAILS`**: élesben zárt a regisztráció. Elég a saját címedet megadnod
  (több cím vesszővel elválasztva). A család többi tagját a felületről hívod meg, a meghívó
  egyben engedély is a regisztrációra (ADR-0004).
- **`JWT_SECRET` előállítása** (PowerShell, kriptográfiailag erős véletlen):

  ```powershell
  $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
  ```

  Ha a titok megváltozik, minden bejelentkezett felhasználónak újra be kell lépnie.
- **`JAVA_TOOL_OPTIONS`**: a Railway a ténylegesen használt memória után számláz, a JVM pedig
  alapból a gép memóriájának nagy részét lefoglalhatná. A fix, kis heap (384 MB) és a soros
  szemétgyűjtő egy családi használatú alkalmazásnak bőven elég, és alacsonyan tartja a költséget.

Mentés után a Railway újratelepít.

## 4. Publikus cím

**Settings → Networking → Generate Domain.** Ingyenes `<név>.up.railway.app` címet kapsz HTTPS-sel
(saját domain is beköthető ugyanitt). Ha a Railway rákérdez a portra, azt a portot add meg, amit
a `PORT` változóban ad (alapesetben automatikusan felismeri).

A HTTPS-t a Railway proxyja zárja le; az alkalmazás a `X-Forwarded-*` fejlécekből tudja, hogy
`https://` linkeket kell generálnia (`server.forward-headers-strategy` a prod profilban).

## 5. Telepítés csak zöld CI után

**Settings → Deploy → Wait for CI**: a Railway csak akkor telepít, ha a GitHub Actions
ellenőrzései (`./mvnw verify`, Docker build) zöldek. Így piros build soha nem kerül ki.

A health check (`/actuator/health/readiness`) a `railway.json`-ban van: az új verzió csak akkor
kap forgalmat, ha az alkalmazás elindult és az adatbázist eléri; különben a régi fut tovább.

## 6. Első belépés, család meghívása, admin

1. Nyisd meg a publikus címet, és regisztrálj a `REGISTRATION_ALLOWED_EMAILS`-ben megadott címmel.
2. Hozd létre a háztartást, majd a **Megosztás** fülön add meg a családtagok e-mail-címét.
   Akinek még nincs fiókja, meghívót kap: amikor ezzel a címmel regisztrál, automatikusan látja
   a háztartást. (Értesítő e-mail nem megy ki, a címet neked kell elküldened nekik.)
3. Biztosító felvételéhez admin szerepkör kell. A `Postgres` szolgáltatásnál a **Data** fülön
   (vagy a Railway CLI-vel: `railway connect Postgres`) futtasd:

   ```sql
   UPDATE app_user SET role = 'ADMIN' WHERE email = 'a-te-cimed@example.com';
   ```

4. Jelentkezz ki és be, hogy az új token már az admin szerepkört tartalmazza.

## E-mail

A napi emlékeztető SMTP-n küld. Egyes platformok a kisebb csomagokon korlátozzák a kimenő SMTP-t;
ha a levelek nem érkeznek meg, a Railway logjában (**Deployments → View Logs**, keresés:
`reminder`) látszik a hiba. Ilyenkor:

- ideiglenesen kapcsold ki: `REMINDER_ENABLED=false`, az alkalmazás többi része ettől működik;
- tartós megoldás egy HTTP-alapú e-mail API (pl. Resend, Brevo API) – ez kisebb
  kódmódosítást igényel a küldő oldalon.

## Költség és erőforrás

- A **Usage** oldalon látszik az alkalmazás és az adatbázis aktuális felhasználása.
- A fiókbeállításokban **usage limit** állítható be, így nem lehet meglepetés a számlán.

## Mentés

- A `Postgres` szolgáltatásnál a **Backups** fülön (ha a csomagod támogatja) ütemezett mentés
  kapcsolható be.
- Kézi mentés a gépedről (kell hozzá a PostgreSQL kliens, a kapcsolódási URL a `Postgres`
  szolgáltatás változói között: `DATABASE_PUBLIC_URL`):

  ```bash
  pg_dump "<DATABASE_PUBLIC_URL>" --format=custom --file=familyfleet-backup.dump
  ```

## Frissítés és visszaállás

- Minden `main`-re merge-elt PR után a Railway automatikusan buildel és telepít (zöld CI esetén).
- Hibás verziónál a **Deployments** listában egy korábbi telepítés **Redeploy**-jal visszaállítható.
  Az adatbázis-migrációk (Flyway) visszafelé nem futnak le, ezért sémaváltozásnál a visszaállás
  előtt érdemes mentést készíteni.

## Biztonsági ellenőrzőlista a cím megosztása előtt

- [ ] Erős, véletlen `JWT_SECRET`
- [ ] `API_DOCS_ENABLED` nincs beállítva (a Swagger UI élesben ki van kapcsolva)
- [ ] `REGISTRATION_ALLOWED_EMAILS` beállítva, `REGISTRATION_OPEN` nincs beállítva (zárt regisztráció)
- [ ] A háztartás megosztva a családtagokkal (Megosztás fül)
- [ ] Mentés bekapcsolva
