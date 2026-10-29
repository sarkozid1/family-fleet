package hu.familyfleet.household;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.household.dto.SharingEntryResponse;
import hu.familyfleet.security.AppUser;
import hu.familyfleet.security.AppUserRepository;
import hu.familyfleet.security.InvitationLookup;
import hu.familyfleet.security.UserRegisteredEvent;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Háztartás megosztása: aki hozzáfér egy háztartáshoz, más e-mail-címeket is beengedhet.
 *
 * <ul>
 *   <li>Regisztrált címnél azonnal hozzáférés jön létre.</li>
 *   <li>Még nem regisztrált címnél meghívó jön létre; ez zárt regisztrációnál engedély is a
 *       regisztrációra, és a regisztrációkor automatikusan hozzáféréssé válik.</li>
 * </ul>
 *
 * Minden hozzáféréssel rendelkező egyenrangú (nincs külön "tulajdonos"): egy családnál ez a
 * legegyszerűbb, és az utolsó hozzáférés visszavonása tiltott, így a háztartás nem árvulhat el.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseholdSharingService implements InvitationLookup {

    private final HouseholdRepository householdRepository;
    private final HouseholdAccessRepository accessRepository;
    private final HouseholdInvitationRepository invitationRepository;
    private final AppUserRepository userRepository;
    private final HouseholdAccessGuard accessGuard;

    public List<SharingEntryResponse> list(Long householdId) {
        accessGuard.requireHousehold(householdId);
        Long currentUserId = accessGuard.currentUserId();
        return Stream.concat(
                        accessRepository.findByHouseholdIdOrderByCreatedAtAsc(householdId).stream()
                                .map(access -> SharingEntryResponse.from(access, currentUserId)),
                        invitationRepository.findByHouseholdIdOrderByCreatedAtAsc(householdId).stream()
                                .map(SharingEntryResponse::from))
                .toList();
    }

    @Transactional
    public SharingEntryResponse share(Long householdId, String email) {
        accessGuard.requireHousehold(householdId);
        Household household = householdRepository.getReferenceById(householdId);
        String normalized = AppUser.normalizeEmail(email);
        AppUser currentUser = userRepository.getReferenceById(accessGuard.currentUserId());

        return userRepository
                .findByEmail(normalized)
                .map(user -> grantAccess(household, user))
                .orElseGet(() -> invite(household, normalized, currentUser));
    }

    /** Az utolsó hozzáférés nem vonható vissza, különben senki nem érné el többé a háztartást. */
    @Transactional
    public void revokeUser(Long householdId, Long userId) {
        accessGuard.requireHousehold(householdId);
        HouseholdAccess access = accessRepository
                .findByUserIdAndHouseholdId(userId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hozzáférés", userId));
        if (accessRepository.countByHouseholdId(householdId) <= 1) {
            throw new ConflictException("Az utolsó hozzáférés nem vonható vissza: a háztartás elérhetetlenné válna.");
        }
        accessRepository.delete(access);
    }

    @Transactional
    public void revokeInvitation(Long householdId, Long invitationId) {
        accessGuard.requireHousehold(householdId);
        HouseholdInvitation invitation = invitationRepository
                .findByIdAndHouseholdId(invitationId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Meghívó", invitationId));
        invitationRepository.delete(invitation);
    }

    /** Regisztrációkor (ugyanabban a tranzakcióban) a címre szóló meghívókból hozzáférés lesz. */
    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        AppUser user = userRepository.getReferenceById(event.userId());
        for (HouseholdInvitation invitation : invitationRepository.findByEmail(event.email())) {
            if (!accessRepository.existsByUserIdAndHouseholdId(
                    event.userId(), invitation.getHousehold().getId())) {
                accessRepository.save(new HouseholdAccess(user, invitation.getHousehold()));
            }
            invitationRepository.delete(invitation);
        }
    }

    @Override
    public boolean hasPendingInvitation(String normalizedEmail) {
        return invitationRepository.existsByEmail(normalizedEmail);
    }

    private SharingEntryResponse grantAccess(Household household, AppUser user) {
        if (accessRepository.existsByUserIdAndHouseholdId(user.getId(), household.getId())) {
            throw new ConflictException("Ez a felhasználó már hozzáfér a háztartáshoz.");
        }
        HouseholdAccess access = accessRepository.saveAndFlush(new HouseholdAccess(user, household));
        return SharingEntryResponse.from(access, accessGuard.currentUserId());
    }

    private SharingEntryResponse invite(Household household, String normalizedEmail, AppUser invitedBy) {
        if (invitationRepository.existsByHouseholdIdAndEmail(household.getId(), normalizedEmail)) {
            throw new ConflictException("Erre az e-mail-címre már van meghívó ebbe a háztartásba.");
        }
        HouseholdInvitation invitation =
                invitationRepository.saveAndFlush(new HouseholdInvitation(household, normalizedEmail, invitedBy));
        return SharingEntryResponse.from(invitation);
    }
}
