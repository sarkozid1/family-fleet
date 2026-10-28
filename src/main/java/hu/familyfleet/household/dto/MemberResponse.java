package hu.familyfleet.household.dto;

import hu.familyfleet.household.HouseholdMember;
import java.time.Instant;

public record MemberResponse(Long id, Long householdId, String name, String email, Instant createdAt) {

    /** A {@code getHousehold().getId()} nem tölti be a lazy proxyt, az azonosító a proxyban már benne van. */
    public static MemberResponse from(HouseholdMember member) {
        return new MemberResponse(
                member.getId(),
                member.getHousehold().getId(),
                member.getName(),
                member.getEmail(),
                member.getCreatedAt());
    }
}
