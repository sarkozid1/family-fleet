package hu.familyfleet.reminder;

import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineType;
import hu.familyfleet.household.Household;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderMessageBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Household household = new Household("Kovács család");
    private final Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");

    @Test
    void groupsDeadlinesByDaysLeftWithMostUrgentFirst() {
        Deadline inMonth = deadline(DeadlineType.VEHICLE_TAX, TODAY.plusDays(30), "Gépjárműadó – ABC123");
        Deadline tomorrow = deadline(DeadlineType.OIL_CHANGE, TODAY.plusDays(1), null);
        Deadline inWeek = deadline(DeadlineType.TECHNICAL_INSPECTION, TODAY.plusDays(7), "Kovács szerviz");

        String body = ReminderMessageBuilder.body("Kovács család", TODAY, List.of(inMonth, tomorrow, inWeek));

        assertThat(body)
                .startsWith("Kedves Kovács család!")
                .contains("  - 2026-10-29 – Olajcsere\n")
                .contains("  - 2026-11-04 – Műszaki vizsga: Kovács szerviz\n")
                .contains("  - 2026-11-27 – Gépjárműadó: Gépjárműadó – ABC123\n");
        assertThat(body.indexOf("Holnap:")).isLessThan(body.indexOf("7 nap múlva:"));
        assertThat(body.indexOf("7 nap múlva:")).isLessThan(body.indexOf("30 nap múlva:"));
    }

    @Test
    void subjectContainsDate() {
        assertThat(ReminderMessageBuilder.subject(TODAY)).isEqualTo("Family Fleet – közelgő határidők (2026-10-28)");
    }

    private Deadline deadline(DeadlineType type, LocalDate due, String title) {
        Deadline deadline = new Deadline(household, type, due, vehicle, null, null);
        deadline.describe(title, null);
        return deadline;
    }
}
