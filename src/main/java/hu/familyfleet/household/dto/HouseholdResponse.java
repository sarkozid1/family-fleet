package hu.familyfleet.household.dto;

import hu.familyfleet.household.Household;

import java.time.Instant;

public record HouseholdResponse(Long id, String name, Instant createdAt) {

    public static HouseholdResponse from(Household household) {
        return new HouseholdResponse(household.getId(), household.getName(), household.getCreatedAt());
    }
}
