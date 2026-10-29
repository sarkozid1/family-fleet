package hu.familyfleet.household;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdAccessRepository extends JpaRepository<HouseholdAccess, Long> {

    boolean existsByUserIdAndHouseholdId(Long userId, Long householdId);

    /** A felhasználók nevével és e-mailjével együtt (egy JOIN, nincs N+1). */
    @EntityGraph(attributePaths = "user")
    List<HouseholdAccess> findByHouseholdIdOrderByCreatedAtAsc(Long householdId);

    Optional<HouseholdAccess> findByUserIdAndHouseholdId(Long userId, Long householdId);

    long countByHouseholdId(Long householdId);
}
