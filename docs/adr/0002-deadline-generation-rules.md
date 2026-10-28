# ADR-0002: Határidők generálása és ismétlődése

## Státusz
Elfogadva

## Kontextus
A legtöbb határidő naptárszabályból vagy egy másik adatból (kötvény, jármű) következik. Ha
ezeket kézzel kell felvenni, a felhasználó elfelejti, és az emlékeztető értelmét veszti. A
szabályok ugyanakkor országspecifikusak, és vannak kényes határesetek (hónap vége, szökőév).
Az ADR-0001 szerint a típusfüggő kötelező kapcsolatot csak a service ellenőrzi.

## Döntés

### Kötelező kapcsolat típusonként
| Típus | Kötelező |
|---|---|
| TECHNICAL_INSPECTION, OIL_CHANGE, TIRE_CHANGE, VEHICLE_TAX, MOTORWAY_VIGNETTE | jármű |
| DRIVING_LICENCE | családtag |
| INSURANCE_PAYMENT, KGFB_ANNIVERSARY | kötvény (a jármű a kötvényből öröklődik) |
| OTHER | – |

Minden kapcsolt elemnek a határidő háztartásához kell tartoznia. A szabályt a `Deadline`
konstruktora kényszeríti ki. Az ADR-0001 hátrányát pótolva a V4-migráció
`ck_deadline_required_link` CHECK-megkötése az adatbázisban is.

### Automatikus generálás
| Esemény | Generált határidő | Esedékesség |
|---|---|---|
| Jármű felvétele `generateTaxDeadline: true`-val | VEHICLE_TAX | a következő márc. 15. vagy szept. 15. (aznap is számít) |
| KGFB-kötvény létrehozása | KGFB_ANNIVERSARY | a következő évforduló (szigorúan a mai nap után) |
| Bármely kötvény létrehozása | INSURANCE_PAYMENT | a következő fizetési nap a gyakoriság szerint |

- A KGFB-évfordulónál a cím és a megjegyzés tartalmazza a felmondási határidőt (évforduló − 30 nap).
- Ha a kötvénynek van záró dátuma, és a számított nap nem esik a záró nap elé, nem generálunk.
- Határidő-létrehozáskor a `dueDate` elhagyható a VEHICLE_TAX, MOTORWAY_VIGNETTE (a következő
  év jan. 31.), KGFB_ANNIVERSARY és INSURANCE_PAYMENT típusnál. A többinél kötelező.

### Ismétlődés teljesítéskor
| Típus | Következő esedékesség | Alap |
|---|---|---|
| VEHICLE_TAX | a következő márc. 15. / szept. 15. | előző esedékesség |
| MOTORWAY_VIGNETTE | a következő év jan. 31. | előző esedékesség |
| INSURANCE_PAYMENT | a következő fizetési nap (a kötvény lejártáig) | kötvény kezdőnapja |
| KGFB_ANNIVERSARY | a következő évforduló (a kötvény lejártáig) | kötvény kezdőnapja |
| TIRE_CHANGE | + 6 hónap | előző esedékesség |
| TECHNICAL_INSPECTION | + 2 év | teljesítés napja |
| OIL_CHANGE | + 1 év | teljesítés napja |
| DRIVING_LICENCE, OTHER | nincs | – |

- A kliens a `complete` kérésben megadhat `nextDueDate`-et: ez felülírja a szabályt, és nem
  ismétlődő típusnál is létrehozza a következő határidőt. Csak a jelenlegi esedékesség utáni
  dátum fogadható el.
- Naptárhoz kötött típusnál az előző esedékességből számolunk, hogy a késve teljesítés ne
  tolja el a naptárat. Az elvégzéshez kötött típusoknál (műszaki vizsga, olajcsere) a
  teljesítés napjából, mert az érvényesség attól fut.

### Dátumszámítás
- Minden számítás a tiszta `DeadlineCalendar` és `DeadlineRecurrence` osztályokban van. A „mai
  napot” a hívó adja át az injektált `Clock`-ból (Europe/Budapest).
- Az évforduló és a fizetési nap mindig a kötvény **kezdőnapjából** számol
  (`start.plusYears(n)`, `start.plusMonths(k * lépés)`), nem az előző esedékességből láncolva.
  Így a jan. 31-i kezdés a rövid hónapokban a hónap utolsó napjára esik, utána visszaáll 31-re.
  A febr. 29-i kezdés nem szökőévben febr. 28-ra esik, szökőévben visszaáll 29-re.

### Megvalósítás
A jármű- és kötvény-service domain-eseményt publikál (`VehicleCreatedEvent`,
`PolicyCreatedEvent`). A `DeadlineGenerator` szinkron `@EventListener`-rel, ugyanabban a
tranzakcióban generál. Így a `vehicle` és `insurance` csomag nem függ a `deadline` csomagtól.

## Következmények
- **Előny:** a gyakori határidők maguktól létrejönnek, és teljesítéskor továbbgörgetődnek; a
  szabályok egy helyen vannak, Spring nélkül, paraméterezett tesztekkel ellenőrizve.
- **Előny:** a generálás atomikus a kiváltó művelettel; ha elbukik, a jármű vagy kötvény sem jön létre.
- **Hátrány:** a kötvény utólagos módosítása (kezdőnap, gyakoriság, záró dátum) nem frissíti a
  már generált határidőket. Ezeket a felhasználónak kell elvetnie vagy módosítania.
- **Hátrány:** a magyar szabályok (adó-esedékesség, matrica, 30 napos felmondás, 2 éves
  műszaki) a kódban vannak rögzítve. Jogszabályváltozásnál kódmódosítás kell, illetve a
  műszaki vizsgánál az első, 4 éves érvényesség a `nextDueDate` megadásával kezelhető.
- **Hátrány:** a szinkron eseménykezelés lassíthatja a létrehozó kérést. Ha a generálás
  drágává válna, `@TransactionalEventListener(AFTER_COMMIT)` + aszinkron feldolgozás lehet a
  következő lépés, a konzisztencia lazításának árán.
