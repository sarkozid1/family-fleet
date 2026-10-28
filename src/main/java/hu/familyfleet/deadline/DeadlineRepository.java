package hu.familyfleet.deadline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface DeadlineRepository extends JpaRepository<Deadline, Long>, JpaSpecificationExecutor<Deadline> {

    /**
     * Nyitott határidők a megadott napig (a már lejártakat is beleértve).
     *
     * <p>A státusz szándékosan <em>literál</em> a JPQL-ben, nem paraméter: így a generált SQL-ben
     * {@code status = 'OPEN'} áll, és a PostgreSQL a {@code idx_deadline_open_due} részleges
     * indexet (WHERE status = 'OPEN') prepared statement általános terve esetén is használhatja.
     * Paraméterrel ({@code status = ?}) a tervező nem tudná előre, hogy a feltétel illeszkedik-e
     * az index predikátumára.
     */
    @Query("""
            select d from Deadline d
            where d.household.id = :householdId
              and d.status = hu.familyfleet.deadline.DeadlineStatus.OPEN
              and d.dueDate <= :until
            order by d.dueDate, d.id
            """)
    List<Deadline> findOpenDueUntil(Long householdId, LocalDate until);
}
