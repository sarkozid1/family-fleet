package hu.familyfleet.reminder;

import hu.familyfleet.deadline.DeadlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * A napi emlékeztető-futás: megkeresi az érintett háztartásokat, és mindegyiknek külön
 * tranzakcióban küld (egy háztartás hibája nem akasztja meg a többit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    /** Egy futás összesítője a loghoz és a tesztekhez. */
    public record RunResult(LocalDate date, int sent, int skipped, int failed) {}

    private final DeadlineRepository deadlineRepository;
    private final HouseholdReminderSender sender;
    private final ReminderProperties properties;
    private final Clock clock;

    public RunResult sendDailyReminders() {
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> dueDates = properties.daysBefore().stream().map(today::plusDays).toList();

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Long householdId : deadlineRepository.findHouseholdIdsWithOpenDueOn(dueDates)) {
            try {
                HouseholdReminderSender.Outcome outcome = sender.send(householdId, today, dueDates);
                log.debug("Reminder for household {}: {}", householdId, outcome);
                if (outcome == HouseholdReminderSender.Outcome.SENT) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (DataIntegrityViolationException e) {
                // Egy párhuzamosan futó példány közben már elküldte (uq_reminder_log_household_date).
                log.info("Reminder for household {} on {} was already sent by another run", householdId, today);
                skipped++;
            } catch (MailException e) {
                log.error("Could not send reminder for household {}; will retry on next run", householdId, e);
                failed++;
            }
        }
        RunResult result = new RunResult(today, sent, skipped, failed);
        log.info("Daily reminder run finished: {}", result);
        return result;
    }
}
