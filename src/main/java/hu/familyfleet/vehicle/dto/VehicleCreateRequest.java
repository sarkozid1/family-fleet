package hu.familyfleet.vehicle.dto;

import hu.familyfleet.vehicle.VehicleType;
import jakarta.validation.constraints.*;

/** Jármű felvétele. A korlátok a {@code vehicle} tábla oszlopait és CHECK-megkötéseit követik. */
public record VehicleCreateRequest(
        @NotNull VehicleType type,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotBlank @Size(max = 10) @Pattern(regexp = LicensePlates.PATTERN, message = LicensePlates.MESSAGE)
        String licensePlate,
        @Pattern(regexp = "^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$", message = "a VIN 17 karakter, I, O és Q betű nélkül")
        String vin,
        @Min(1900) @Max(2100) Short productionYear,
        @PositiveOrZero Integer currentMileage,
        Long ownerId,
        /* Ha true, a következő gépjárműadó-esedékesség határidőként rögzül (pl. elektromos autónál nincs adó). */
        Boolean generateTaxDeadline) {

    public boolean shouldGenerateTaxDeadline() {
        return Boolean.TRUE.equals(generateTaxDeadline);
    }
}
