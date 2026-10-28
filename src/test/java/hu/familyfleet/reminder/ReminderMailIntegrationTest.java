package hu.familyfleet.reminder;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        // Egyedi címek: a közös tesztadatbázisban más tesztek háztartásai is kaphatnak levelet.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String anna = "anna-" + suffix + "@example.com";
        String bence = "bence-" + suffix + "@example.com";
        Household household = householdRepository.save(new Household("Emlékeztetett család"));
        memberRepository.save(new HouseholdMember(household, "Anna", anna));
        memberRepository.save(new HouseholdMember(household, "Bence", bence));
        Vehicle vehicle = vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "EML001"));
        save(household, vehicle, DeadlineType.TECHNICAL_INSPECTION, TODAY.plusDays(7), "Kovács szerviz");
        save(household, vehicle, DeadlineType.OIL_CHANGE, TODAY.plusDays(30), null);
        save(household, vehicle, DeadlineType.TIRE_CHANGE, TODAY.plusDays(5), "Nem emlékeztetési pont");

        ReminderService.RunResult first = reminderService.sendDailyReminders();
        ReminderService.RunResult second = reminderService.sendDailyReminders();

        assertThat(first.sent()).isGreaterThanOrEqualTo(1);
        assertThat(second.sent())
                .as("ugyanarra a napra senki nem kap második levelet")
                .isZero();
        assertThat(reminderLogRepository.existsByHouseholdIdAndReminderDate(household.getId(), TODAY))
                .isTrue();

        String messages = mailpit().get().uri("/api/v1/messages").retrieve().body(String.class);
        List<Map<String, Object>> ours = JsonPath.<List<Map<String, Object>>>read(messages, "$.messages").stream()
                .filter(message ->
                        JsonPath.<List<String>>read(message, "$.To[*].Address").contains(anna))
                .toList();
        assertThat(ours).as("pontosan egy levél ennek a háztartásnak").hasSize(1);
        Map<String, Object> message = ours.getFirst();
        assertThat(message.get("Subject")).isEqualTo("Family Fleet – közelgő határidők (2026-10-28)");
        List<String> recipients = JsonPath.read(message, "$.To[*].Address");
        assertThat(recipients).containsExactlyInAnyOrder(anna, bence);

        String id = (String) message.get("ID");
        String text = JsonPath.read(
                mailpit().get().uri("/api/v1/message/{id}", id).retrieve().body(String.class), "$.Text");
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
