package hu.familyfleet.reminder;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.deadline.DeadlineType;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import hu.familyfleet.vehicle.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valódi levélküldés egy Mailpit SMTP-szerverre (ugyanaz az image, mint a compose.yaml-ben), majd
 * a Mailpit REST API-ján ellenőrizzük, mi érkezett meg. A második futás nem küldhet újra.
 */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class ReminderMailIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.31.6")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.of("Europe/Budapest"));
        }
    }

    @Autowired
    private ReminderService reminderService;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMemberRepository memberRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private DeadlineRepository deadlineRepository;
    @Autowired
    private ReminderLogRepository reminderLogRepository;

    @Test
    void sendsOneSummaryPerHouseholdAndNeverTwiceOnTheSameDay() {
        Household household = householdRepository.save(new Household("Emlékeztetett család"));
        memberRepository.save(new HouseholdMember(household, "Anna", "anna@example.com"));
        memberRepository.save(new HouseholdMember(household, "Bence", "bence@example.com"));
        Vehicle vehicle = vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "EML001"));
        save(household, vehicle, DeadlineType.TECHNICAL_INSPECTION, TODAY.plusDays(7), "Kovács szerviz");
        save(household, vehicle, DeadlineType.OIL_CHANGE, TODAY.plusDays(30), null);
        save(household, vehicle, DeadlineType.TIRE_CHANGE, TODAY.plusDays(5), "Nem emlékeztetési pont");

        ReminderService.RunResult first = reminderService.sendDailyReminders();
        ReminderService.RunResult second = reminderService.sendDailyReminders();

        assertThat(first.sent()).isEqualTo(1);
        assertThat(second.sent()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(reminderLogRepository.existsByHouseholdIdAndReminderDate(household.getId(), TODAY)).isTrue();

        String messages = mailpit().get().uri("/api/v1/messages").retrieve().body(String.class);
        assertThat((Integer) JsonPath.read(messages, "$.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(messages, "$.messages[0].Subject"))
                .isEqualTo("Family Fleet – közelgő határidők (2026-10-28)");
        List<String> recipients = JsonPath.read(messages, "$.messages[0].To[*].Address");
        assertThat(recipients).containsExactlyInAnyOrder("anna@example.com", "bence@example.com");

        String id = JsonPath.read(messages, "$.messages[0].ID");
        String text = JsonPath.read(mailpit().get().uri("/api/v1/message/{id}", id).retrieve().body(String.class), "$.Text");
        assertThat(text)
                .contains("2026-11-04 – Műszaki vizsga: Kovács szerviz")
                .contains("2026-11-27 – Olajcsere")
                .doesNotContain("Nem emlékeztetési pont");
    }

    private void save(Household household, Vehicle vehicle, DeadlineType type, LocalDate due, String title) {
        Deadline deadline = new Deadline(household, type, due, vehicle, null, null);
        deadline.describe(title, null);
        deadlineRepository.save(deadline);
    }

    private static RestClient mailpit() {
        return RestClient.create("http://%s:%d".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025)));
    }
}
