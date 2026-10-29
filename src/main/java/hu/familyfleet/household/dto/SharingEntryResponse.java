package hu.familyfleet.household.dto;

import hu.familyfleet.household.HouseholdAccess;
import hu.familyfleet.household.HouseholdInvitation;
import java.time.Instant;

/**
 * Egy sor a megosztási listában: aktív hozzáférés (felhasználó) vagy függő meghívó.
 *
 * @param userId       aktív hozzáférésnél a felhasználó azonosítója (ezzel vonható vissza)
 * @param invitationId függő meghívónál a meghívó azonosítója (ezzel vonható vissza)
 * @param you          a bejelentkezett felhasználó saját sora
 */
public record SharingEntryResponse(
        Status status, Long userId, Long invitationId, String email, String displayName, boolean you, Instant since) {

    public enum Status {
        ACTIVE,
        PENDING
    }

    public static SharingEntryResponse from(HouseholdAccess access, Long currentUserId) {
        return new SharingEntryResponse(
                Status.ACTIVE,
                access.getUser().getId(),
                null,
                access.getUser().getEmail(),
                access.getUser().getDisplayName(),
                access.getUser().getId().equals(currentUserId),
                access.getCreatedAt());
    }

    public static SharingEntryResponse from(HouseholdInvitation invitation) {
        return new SharingEntryResponse(
                Status.PENDING,
                null,
                invitation.getId(),
                invitation.getEmail(),
                null,
                false,
                invitation.getCreatedAt());
    }
}
