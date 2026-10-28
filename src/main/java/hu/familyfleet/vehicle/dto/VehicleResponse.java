package hu.familyfleet.vehicle.dto;

import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import java.time.Instant;

public record VehicleResponse(
        Long id,
        Long householdId,
        Long ownerId,
        VehicleType type,
        String brand,
        String model,
        String licensePlate,
        String vin,
        Short productionYear,
        Integer currentMileage,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Csak azonosítókat olvas a lazy kapcsolatokból, így listázáskor sem indul plusz lekérdezés (nincs N+1). */
    public static VehicleResponse from(Vehicle vehicle) {
        HouseholdMember owner = vehicle.getOwner();
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getHousehold().getId(),
                owner == null ? null : owner.getId(),
                vehicle.getType(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate(),
                vehicle.getVin(),
                vehicle.getProductionYear(),
                vehicle.getCurrentMileage(),
                vehicle.getVersion(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
