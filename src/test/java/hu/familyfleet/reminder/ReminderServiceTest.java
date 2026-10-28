package hu.familyfleet.reminder;

import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.reminder.HouseholdReminderSender.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** A futás logikája ütemező, adatbázis és SMTP nélkül. */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    // Budapesten már október 28. van, UTC-ben még 27.: a "mai nap" az időzóna szerint számít.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T23:30:00Z"), ZoneId.of("Europe/Budapest"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final List<LocalDate> DUE_DATES =
            List.of(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 10, 29));

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private HouseholdReminderSender sender;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        ReminderProperties properties = new ReminderProperties(true, "0 0 7 * * *", List.of(30, 7, 1), "noreply@test");
        service = new ReminderService(deadlineRepository, sender, properties, CLOCK);
    }

    @Test
    void looksForDeadlinesExactly30And7And1DaysAhead() {
        given(deadlineRepository.findHouseholdIdsWithOpenDueOn(DUE_DATES)).willReturn(List.of());

        ReminderService.RunResult result = service.sendDailyReminders();

        assertThat(result).isEqualTo(new ReminderService.RunResult(TODAY, 0, 0, 0));
        verify(deadlineRepository).findHouseholdIdsWithOpenDueOn(DUE_DATES);
    }

    @Test
    void countsOutcomesAndKeepsGoingAfterFailures() {
        given(deadlineRepository.findHouseholdIdsWithOpenDueOn(DUE_DATES)).willReturn(List.of(1L, 2L, 3L, 4L, 5L));
        given(sender.send(1L, TODAY, DUE_DATES)).willReturn(Outcome.SENT);
        given(sender.send(2L, TODAY, DUE_DATES)).willReturn(Outcome.ALREADY_SENT);
        given(sender.send(3L, TODAY, DUE_DATES)).willThrow(new MailSendException("SMTP nem elérhető"));
        given(sender.send(4L, TODAY, DUE_DATES)).willThrow(new DataIntegrityViolationException("uq_reminder_log_household_date"));
        given(sender.send(5L, TODAY, DUE_DATES)).willReturn(Outcome.SENT);

        ReminderService.RunResult result = service.sendDailyReminders();

        assertThat(result).isEqualTo(new ReminderService.RunResult(TODAY, 2, 2, 1));
    }
}
