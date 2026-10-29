package hu.familyfleet.household;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, Long> {

    boolean existsByEmail(String email);

    boolean existsByHouseholdIdAndEmail(Long householdId, String email);

    List<HouseholdInvitation> findByEmail(String email);

    List<HouseholdInvitation> findByHouseholdIdOrderByCreatedAtAsc(Long householdId);

    Optional<HouseholdInvitation> findByIdAndHouseholdId(Long id, Long householdId);
}
