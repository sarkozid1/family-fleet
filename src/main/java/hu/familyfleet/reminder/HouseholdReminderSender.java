package hu.familyfleet.reminder;

import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Egy háztartás napi emlékeztetője, saját tranzakcióban.
 *
 * <p>Sorrend: (1) van-e már napló erre a napra, (2) a napló beszúrása és flush – ezzel
 * "lefoglaljuk" a napot; párhuzamos futásnál itt az egyedi megkötés megállítja a második
 * példányt, (3) levélküldés. Ha a küldés hibát dob, a tranzakció visszagörgeti a naplót,
 * így a következő futás újrapróbálhatja.
 */
@Component
@RequiredArgsConstructor
class HouseholdReminderSender {

    enum Outcome { SENT, ALREADY_SENT, NOTHING_DUE, NO_RECIPIENTS }

    private final ReminderLogRepository logRepository;
    private final DeadlineRepository deadlineRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final MailSender mailSender;
    private final ReminderProperties properties;
    private final Clock clock;

    @Transactional
    public Outcome send(Long householdId, LocalDate today, Collection<LocalDate> dueDates) {
        if (logRepository.existsByHouseholdIdAndReminderDate(householdId, today)) {
            return Outcome.ALREADY_SENT;
        }
        List<Deadline> deadlines = deadlineRepository.findOpenDueOn(householdId, dueDates);
        if (deadlines.isEmpty()) {
            return Outcome.NOTHING_DUE;
        }
        String[] recipients = memberRepository.findByHouseholdIdOrderByNameAsc(householdId).stream()
                .map(HouseholdMember::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .toArray(String[]::new);
        if (recipients.length == 0) {
            return Outcome.NO_RECIPIENTS;
        }
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Háztartás", householdId));

        logRepository.saveAndFlush(new ReminderLog(household, today, recipients.length, deadlines.size(), clock.instant()));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipients);
        message.setSubject(ReminderMessageBuilder.subject(today));
        message.setText(ReminderMessageBuilder.body(household.getName(), today, deadlines));
        mailSender.send(message);
        return Outcome.SENT;
    }
}
