# 01 – README és a céges munkafolyamat

## Mit csináltunk és miért?

Frissítettük a README-t: követelmények, indítás, tesztfuttatás, technológiák, projektfelépítés,
és linkek az ADR-ekre és ezekre a tanulási jegyzetekre. A fájl UTF-8 kódolású, **BOM nélkül**.

Ez az első PR, amely már a teljes céges munkafolyamatot követi:

1. **GitHub Issue** – leírja a célt és az elfogadási feltételeket (mikor „kész” a feladat).
2. **Branch** az Issue számával: `docs/9-readme`. A névből látszik a típus és a kapcsolódó jegy.
3. **Conventional Commits**: `docs(#9): update README`. A típus (`feat`, `fix`, `docs`, `test`,
   `chore`, `refactor`, `ci`) géppel olvasható, ebből changelog és verziószám is generálható.
4. **`./mvnw verify` zöld** – a PR előtt helyben lefut minden teszt.
5. **Pull Request** – egységes szerkezetű leírás, `Closes #9`, ami merge-kor automatikusan lezárja
   az Issue-t.
6. **Squash merge** – a branch összes commitja egyetlen commitként kerül a `main`-re, így a
   `main` története tiszta: egy commit = egy logikai változás = egy PR.

## Kulcsfogalmak

**README** – a projekt „bejárati ajtaja”. Egy új kolléga ebből tudja meg, mi ez, hogyan indítja,
hogyan teszteli. Ha a README elavult, minden új belépő ugyanazokat a kérdéseket teszi fel.

**BOM (Byte Order Mark)** – a fájl elején álló láthatatlan `EF BB BF` bájtsorozat. Windows-os
szerkesztők néha beírják az UTF-8 fájlok elejére. Gondot okozhat: a GitHub megjeleníti, egyes
eszközök (shell-szkriptek, YAML- és SQL-parserek) hibásan olvassák az első sort. UTF-8-nál nincs
rá szükség, ezért a konvenció: UTF-8 BOM nélkül.

**Conventional Commits** – `típus(hatókör): rövid leírás` formátum. Mi a hatókörbe az Issue
számát tesszük (`feat(#12): ...`), így minden commit visszakövethető a jegyig.

**Squash merge vs. merge commit vs. rebase** – a squash egy commitba tömöríti a PR-t. Előnye a
tiszta, lineáris history és az egyszerű `git revert`; hátránya, hogy a branch részletes commitjai
elvesznek a `main`-ről (a PR-ban viszont megmaradnak).

**ADR (Architecture Decision Record)** – rövid dokumentum egy architekturális döntésről:
kontextus, döntés, következmények. Fél év múlva ebből derül ki, *miért* olyan a kód, amilyen.

## Interjún így mondd

> „A projektben céges munkafolyamatot követtem: minden változás Issue-ból indult, külön branchen,
> Conventional Commits üzenetekkel készült, és csak zöld build után, PR-on keresztül, squash
> merge-dzsel került a main-re. A PR-leírásokban mindig indokoltam a fontosabb döntéseket, a
> nagyobb architekturális döntésekhez ADR-t írtam. A README-ből bárki el tudja indítani a
> projektet egyetlen paranccsal, mert a Spring Boot Docker Compose támogatása elindítja az
> adatbázist, a tesztek pedig Testcontainersszel saját adatbázist kapnak.”

## Valószínű interjúkérdések

1. **Miért jó, ha minden változás Issue-ból indul?**
   Visszakövethetőség: a commit → PR → Issue láncból kiderül, milyen igény miatt változott a kód.
   Az Issue elfogadási feltételei pedig rögzítik, mikor kész a munka.

2. **Mi a különbség a merge, a squash és a rebase között?**
   A merge commit megőrzi a branch teljes történetét és egy összefűző commitot hoz létre. A squash
   egyetlen új commitba tömöríti a változásokat. A rebase a branch commitjait a cél ágra „játssza
   rá”, lineáris, de az összes commitot megtartó historyt adva.

3. **Mi az a Conventional Commits, és mire jó?**
   Egységes commitüzenet-formátum (`feat`, `fix`, `docs`...). Olvashatóbb a history, és
   automatizálható belőle a changelog és a szemantikus verziózás (a `feat` minor, a `fix` patch).

4. **Mit tartalmaz egy jó README?**
   Mi a projekt, mi kell a futtatáshoz, hogyan indítom, hogyan tesztelem, milyen technológiák,
   hol a további dokumentáció. Parancsokat kódblokkban, hogy másolhatók legyenek.

5. **Mi az az ADR, és mikor írsz ilyet?**
   Architekturális döntés rövid leírása (kontextus, döntés, következmények). Akkor, ha egy döntés
   nehezen visszafordítható vagy nem magától értetődő – pl. közös határidőtábla vs. típusonkénti
   táblák.

6. **Miért kell Docker a tesztekhez?**
   Mert a tesztek valódi PostgreSQL ellen futnak Testcontainersszel, nem H2-vel. Így ugyanaz az
   SQL-dialektus, ugyanazok a megkötések és a Flyway-migrációk is tesztelve vannak.

7. **Mi az a Maven Wrapper?**
   A projektbe tett szkript (`mvnw`, `mvnw.cmd`), ami a rögzített Maven-verziót tölti le és
   használja. Így mindenkinél és a CI-ban is ugyanaz a Maven fut, nem kell külön telepíteni.
