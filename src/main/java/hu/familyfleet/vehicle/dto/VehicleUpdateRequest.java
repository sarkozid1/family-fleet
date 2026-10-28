package hu.familyfleet.vehicle.dto;

import hu.familyfleet.vehicle.VehicleType;
import jakarta.validation.constraints.*;

/**
 * Jármű teljes módosítása (PUT). A {@code version} a korábban letöltött állapot verziója:
 * ha közben más módosította a járművet, a kérés 409-cel elutasításra kerül.
 */
public record VehicleUpdateRequest(
        @NotNull VehicleType type,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotBlank @Size(max = 10) @Pattern(regexp = LicensePlates.PATTERN, message = LicensePlates.MESSAGE)
        String licensePlate,
        @Pattern(regexp = "^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$", message = "a VIN 17 karakter, I, O és Q betű nélkül")
        String vin,
        @Min(1900) @Max(2100) Short productionYear,
        @NotNull @PositiveOrZero Integer currentMileage,
        Long ownerId,
        @NotNull Long version) {}
