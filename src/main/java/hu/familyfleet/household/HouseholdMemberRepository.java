package hu.familyfleet.household;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findByHouseholdIdOrderByNameAsc(Long householdId);

    Optional<HouseholdMember> findByIdAndHouseholdId(Long id, Long householdId);
}
