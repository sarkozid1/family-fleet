-- =========================================================
-- V4: Határidő-megkötések és index
-- =========================================================

-- FK-index a háztartásra. A V1 részleges indexe (idx_deadline_open_due) csak a nyitott
-- határidőket tartalmazza, így a lezártak szűrésére és az FK-ellenőrzésre (household törlése)
-- nem használható. Szándékosan csak household_id: a "közelgő nyitott határidők" lekérdezéshez
-- a részleges (household_id, due_date) index marad a jobb választás.
CREATE INDEX idx_deadline_household ON deadline (household_id);

-- Teljesítési időpont csak teljesített (DONE) határidőnél van, és ott kötelező.
ALTER TABLE deadline
    ADD CONSTRAINT ck_deadline_completed_at
        CHECK ((status = 'DONE') = (completed_at IS NOT NULL));

-- Típusfüggő kötelező kapcsolat (ADR-0001 hátránya, ADR-0002 pótolja): a service is ellenőrzi,
-- az adatbázis az utolsó védvonal.
ALTER TABLE deadline
    ADD CONSTRAINT ck_deadline_required_link
        CHECK (CASE type
                   WHEN 'DRIVING_LICENCE'   THEN member_id IS NOT NULL
                   WHEN 'INSURANCE_PAYMENT' THEN policy_id IS NOT NULL
                   WHEN 'KGFB_ANNIVERSARY'  THEN policy_id IS NOT NULL
                   WHEN 'OTHER'             THEN TRUE
                   ELSE vehicle_id IS NOT NULL
               END);
