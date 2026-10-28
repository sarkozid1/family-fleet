package hu.familyfleet.household.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Háztartás létrehozása és módosítása. A méretkorlát a {@code household.name VARCHAR(100)} oszlopot követi. */
public record HouseholdRequest(@NotBlank @Size(max = 100) String name) {}
