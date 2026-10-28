package hu.familyfleet.household;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdAccessRepository extends JpaRepository<HouseholdAccess, Long> {

    boolean existsByUserIdAndHouseholdId(Long userId, Long householdId);
}
