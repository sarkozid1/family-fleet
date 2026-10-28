# ADR-0001: Közös határidő-entitás

## Státusz
Elfogadva

## Kontextus
Sokféle lejáratot kell követni: műszaki vizsga, biztosítási díj, gépjárműadó,
matrica, jogosítvány. Ezek egy része járműhöz, más része családtaghoz vagy kötvényhez tartozik.

## Döntés
Egyetlen `deadline` tábla, `type` mezővel és opcionális vehicle/member/policy kapcsolattal.

## Következmények
- **Előny:** egyetlen lekérdezéssel lekérhető minden közelgő határidő; egy emlékeztető-job kezeli mindet.
- **Hátrány:** az adatbázis nem kényszeríti ki, hogy pl. a TECHNICAL_INSPECTION típusnál legyen vehicle_id.
  Ezt a service réteg validálja.
