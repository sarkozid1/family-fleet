package hu.familyfleet.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    boolean existsByHouseholdIdAndReminderDate(Long householdId, LocalDate reminderDate);
}
