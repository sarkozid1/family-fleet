package hu.familyfleet.reminder;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    boolean existsByHouseholdIdAndReminderDate(Long householdId, LocalDate reminderDate);
}
