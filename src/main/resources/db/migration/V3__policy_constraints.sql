-- =========================================================
-- V3: Kötvény- és biztosító-megkötések
-- =========================================================

-- A V1-ben automatikusan elnevezett egyedi megkötés egységes nevet kap (uq_ prefix),
-- hogy a hibakezelő név alapján érthető üzenetet adhasson.
ALTER TABLE insurer RENAME CONSTRAINT insurer_name_key TO uq_insurer_name;

-- Egy járműnek egy időben legfeljebb egy érvényes KGFB-je lehet.
-- Az EXCLUDE megkötés két sort akkor tilt, ha minden felsorolt feltétel egyszerre teljesül:
-- ugyanaz a jármű (=) ÉS átfedő az érvényességi időszak (&&). A daterange '[)' félig nyitott:
-- a záró nap már nem tartozik bele, így a lejáró és az aznap induló új kötvény nem ütközik.
-- A NULL end_date végtelen felső határt jelent (határozatlan idejű KGFB).
-- A btree_gist bővítmény kell ahhoz, hogy a GiST index egyenlőséget (vehicle_id WITH =) is tudjon.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE insurance_policy
    ADD CONSTRAINT ex_policy_kgfb_no_overlap
        EXCLUDE USING gist (
            vehicle_id WITH =,
            daterange(start_date, end_date, '[)') WITH &&
        ) WHERE (policy_type = 'KGFB');
