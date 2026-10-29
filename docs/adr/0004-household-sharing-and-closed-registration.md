# ADR-0004: Háztartás-megosztás és zárt regisztráció

## Státusz
Elfogadva (kiegészíti az ADR-0003-at)

## Kontextus
Az ADR-0003 óta minden felhasználó csak a saját maga által létrehozott háztartást látja, és bárki
regisztrálhat. Egy nyilvános címen (Railway) futó családi alkalmazásnál ez két okból kevés:
a családtagoknak ugyanazt a háztartást kell kezelniük, és idegenek ne hozhassanak létre fiókot.

## Döntés

### Megosztás e-mail-cím alapján
- Aki hozzáfér egy háztartáshoz, megoszthatja egy e-mail-címmel (`POST /api/households/{id}/sharing`).
- **Regisztrált cím:** azonnal `household_access` sor jön létre.
- **Még nem regisztrált cím:** `household_invitation` (meghívó) jön létre. Amikor valaki ezzel a
  címmel regisztrál, a meghívóból ugyanabban a tranzakcióban hozzáférés lesz, a meghívó törlődik
  (`UserRegisteredEvent`).
- **Nincs külön tulajdonosi szerep:** minden hozzáféréssel rendelkező egyenrangú, és bárki
  visszavonhat hozzáférést vagy meghívót. Egy családnál ez a legegyszerűbb és várhatóan elég.
- **Az utolsó hozzáférés nem vonható vissza (409),** hogy a háztartás ne váljon elérhetetlenné.

### Zárt regisztráció élesben
- `app.security.registration.open`: fejlesztésben és tesztben `true`, élesben alapból `false`.
- Zárt módban csak a `REGISTRATION_ALLOWED_EMAILS` címei és a függő meghívóval rendelkezők
  regisztrálhatnak; mindenki más 403-at kap.
- **A meghívó egyben regisztrációs engedély.** Az első szülő az engedélyezett listával
  regisztrál, a többieket a felületről hívja meg, a konfigurációhoz többé nem kell nyúlni.
- **A 403-at a foglalt e-mail-cím ellenőrzése (409) előtt adjuk,** így egy kívülálló nem tudja
  kipróbálni, hogy egy cím regisztrálva van-e.

### Csomagfüggőség
A regisztrációs szabálynak tudnia kell a meghívókról, de a `security` csomag nem függhet a
`household`-tól (fordítva már függ). Ezért a `security` csomag definiálja az `InvitationLookup`
interfészt, és a `household` valósítja meg (függőség-megfordítás). A meghívók beváltása
domain-eseménnyel (`UserRegisteredEvent`) történik.

## Következmények
- **Előny:** a család egy háztartáson dolgozik, és egy nyilvános telepítésen nincs idegen regisztráció.
- **Előny:** a meghívás e-mail-küldés nélkül is működik (a meghívott egyszerűen regisztrál).
- **Hátrány:** a megosztási válasz (`ACTIVE` vagy `PENDING`) elárulja a megosztónak, hogy egy
  cím regisztrálva van-e. Ezt a megosztó már hozzáféréssel rendelkező családtag látja, ezért
  elfogadható.
- **Hátrány:** a meghívónak nincs lejárata, és nincs róla e-mail-értesítés. Ha kell, külön
  fejlesztés.
- **Hátrány:** nincs szerepkör-különbség a háztartáson belül (pl. csak olvasó tag).
