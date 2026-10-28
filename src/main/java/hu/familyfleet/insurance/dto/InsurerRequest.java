package hu.familyfleet.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Új biztosító. A korlátok az {@code insurer} tábla oszlopait követik. */
public record InsurerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 30) @Pattern(regexp = "^[0-9 +()/-]*$", message = "csak számjegy, szóköz és + ( ) / - megengedett")
        String phone,
        @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "http:// vagy https:// kezdetű cím kell")
        String website) {}
