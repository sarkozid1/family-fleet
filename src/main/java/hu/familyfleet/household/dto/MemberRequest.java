package hu.familyfleet.household.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Családtag felvétele. A méretkorlátok a {@code household_member} tábla oszlopait követik. */
public record MemberRequest(
        @NotBlank @Size(max = 100) String name,
        @Email @Size(max = 255) String email) {}
