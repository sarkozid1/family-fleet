package hu.familyfleet.household;

import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adatszintű jogosultság: a bejelentkezett felhasználó hozzáfér-e egy háztartáshoz.
 *
 * <p>Idegen háztartásnál ugyanazt a 404-et adjuk, mint nem létezőnél, hogy az azonosítók
 * létezése ne szivárogjon ki (ADR-0003). Minden háztartáshoz kötött service-művelet ezen megy át.
 */
@Component
@RequiredArgsConstructor
public class HouseholdAccessGuard {

    private final HouseholdAccessRepository accessRepository;
    private final CurrentUser currentUser;

    public boolean hasAccess(Long householdId) {
        return householdId != null && accessRepository.existsByUserIdAndHouseholdId(currentUser.id(), householdId);
    }

    /** Háztartás-útvonalakhoz ({@code /api/households/{id}/...}): nincs hozzáférés → "Háztartás nem található". */
    public void requireHousehold(Long householdId) {
        if (!hasAccess(householdId)) {
            throw new ResourceNotFoundException("Háztartás", householdId);
        }
    }

    /** Közvetett erőforrásokhoz (jármű, kötvény, határidő): az adott erőforrás "nem található". */
    public void requireAccess(Long householdId, String resourceName, Object resourceId) {
        if (!hasAccess(householdId)) {
            throw new ResourceNotFoundException(resourceName, resourceId);
        }
    }

    public Long currentUserId() {
        return currentUser.id();
    }
}
