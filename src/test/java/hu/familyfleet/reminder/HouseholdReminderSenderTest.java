package hu.familyfleet.reminder;

import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.deadline.DeadlineType;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.reminder.HouseholdReminderSender.Outcome;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HouseholdReminderSenderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.of("Europe/Budapest"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final List<LocalDate> DUE_DATES = List.of(TODAY.plusDays(30), TODAY.plusDays(7), TODAY.plusDays(1));

    @Mock
    private ReminderLogRepository logRepository;
    @Mock
    private DeadlineRepository deadlineRepository;
    @Mock
    private HouseholdRepository householdRepository;
    @Mock
    private HouseholdMemberRepository memberRepository;
    @Mock
    private MailSender mailSender;

    private HouseholdReminderSender sender;

    private final Household household = new Household("Kovács család");
    private final Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");
    private final Deadline inspection =
            new Deadline(household, DeadlineType.TECHNICAL_INSPECTION, TODAY.plusDays(7), vehicle, null, null);

    @BeforeEach
    void setUp() {
        ReminderProperties properties =
                new ReminderProperties(true, "0 0 7 * * *", List.of(30, 7, 1), "Family Fleet <noreply@test>");
        sender = new HouseholdReminderSender(logRepository, deadlineRepository, householdRepository, memberRepository,
                mailSender, properties, CLOCK);
    }

    @Test
    void sendsOneMailToEveryMemberWithEmailAfterLoggingTheDay() {
        given(deadlineRepository.findOpenDueOn(1L, DUE_DATES)).willReturn(List.of(inspection));
        given(memberRepository.findByHouseholdIdOrderByNameAsc(1L)).willReturn(List.of(
                new HouseholdMember(household, "Anna", "anna@example.com"),
                new HouseholdMember(household, "Bence", null),
                new HouseholdMember(household, "Csilla", "csilla@example.com")));
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));

        Outcome outcome = sender.send(1L, TODAY, DUE_DATES);

        assertThat(outcome).isEqualTo(Outcome.SENT);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        InOrder order = inOrder(logRepository, mailSender);
        order.verify(logRepository).saveAndFlush(any(ReminderLog.class));
        order.verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("anna@example.com", "csilla@example.com");
        assertThat(message.getValue().getText()).contains("Műszaki vizsga");
    }

    @Test
    void doesNothingWhenAlreadySentToday() {
        given(logRepository.existsByHouseholdIdAndReminderDate(1L, TODAY)).willReturn(true);

        assertThat(sender.send(1L, TODAY, DUE_DATES)).isEqualTo(Outcome.ALREADY_SENT);
        verifyNoInteractions(mailSender, deadlineRepository);
    }

    @Test
    void skipsHouseholdWithoutEmailAddressesWithoutLogging() {
        given(deadlineRepository.findOpenDueOn(1L, DUE_DATES)).willReturn(List.of(inspection));
        given(memberRepository.findByHouseholdIdOrderByNameAsc(1L))
                .willReturn(List.of(new HouseholdMember(household, "Bence", null)));

        assertThat(sender.send(1L, TODAY, DUE_DATES)).isEqualTo(Outcome.NO_RECIPIENTS);
        verify(logRepository, never()).saveAndFlush(any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void mailFailurePropagatesSoTheTransactionRollsBackTheLog() {
        given(deadlineRepository.findOpenDueOn(1L, DUE_DATES)).willReturn(List.of(inspection));
        given(memberRepository.findByHouseholdIdOrderByNameAsc(1L))
                .willReturn(List.of(new HouseholdMember(household, "Anna", "anna@example.com")));
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));
        willThrow(new MailSendException("SMTP nem elérhető")).given(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send(1L, TODAY, DUE_DATES)).isInstanceOf(MailSendException.class);
    }
}
