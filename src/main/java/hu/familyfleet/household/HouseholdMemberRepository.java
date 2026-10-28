package hu.familyfleet.household;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findByHouseholdIdOrderByNameAsc(Long householdId);

    Optional<HouseholdMember> findByIdAndHouseholdId(Long id, Long householdId);
}
