package hu.familyfleet.household.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Háztartás megosztása e-mail-cím alapján (meglévő felhasználóval vagy meghívóval). */
public record ShareRequest(@NotBlank @Email @Size(max = 255) String email) {}
