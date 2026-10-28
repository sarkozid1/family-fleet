package hu.familyfleet.household;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HouseholdRepository extends JpaRepository<Household, Long> {

    @Query("""
            select h from Household h
            where exists (select 1 from HouseholdAccess a where a.household = h and a.user.id = :userId)
            """)
    List<Household> findAccessibleBy(Long userId, Sort sort);
}
