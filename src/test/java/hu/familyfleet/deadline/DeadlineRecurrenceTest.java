package hu.familyfleet.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.Insurer;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeadlineRecurrenceTest {

    private final Household household = new Household("Kovács család");
    private final Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");

    @ParameterizedTest(name = "{0}: esedékes {1}, teljesítve {2} -> {3}")
    @CsvSource({
        // naptárhoz kötött: az esedékességből számolunk, a késés nem tolja el
        "VEHICLE_TAX,          2026-03-15, 2026-04-02, 2026-09-15",
        "VEHICLE_TAX,          2026-09-15, 2026-09-10, 2027-03-15",
        "MOTORWAY_VIGNETTE,    2027-01-31, 2027-01-20, 2028-01-31",
        "TIRE_CHANGE,          2026-10-31, 2026-11-05, 2027-04-30",
        // elvégzéshez kötött: a teljesítés napjától fut az új érvényesség
        "TECHNICAL_INSPECTION, 2026-11-01, 2026-10-20, 2028-10-20",
        "TECHNICAL_INSPECTION, 2026-11-01, 2026-11-15, 2028-11-15",
        "OIL_CHANGE,           2026-11-01, 2026-12-01, 2027-12-01",
        "OIL_CHANGE,           2028-02-29, 2028-02-29, 2029-02-28"
    })
    void vehicleTypes(DeadlineType type, LocalDate due, LocalDate completedOn, LocalDate expected) {
        Deadline deadline = new Deadline(household, type, due, vehicle, null, null);

        assertThat(DeadlineRecurrence.nextDueDate(deadline, completedOn)).contains(expected);
    }

    @Test
    void drivingLicenceAndOtherDoNotRecur() {
        HouseholdMember anna = new HouseholdMember(household, "Anna", null);

        assertThat(DeadlineRecurrence.nextDueDate(
                        new Deadline(
                                household, DeadlineType.DRIVING_LICENCE, LocalDate.of(2030, 5, 1), null, anna, null),
                        LocalDate.of(2030, 4, 1)))
                .isEmpty();
        assertThat(DeadlineRecurrence.nextDueDate(
                        new Deadline(household, DeadlineType.OTHER, LocalDate.of(2026, 12, 1), null, null, null),
                        LocalDate.of(2026, 12, 1)))
                .isEmpty();
    }

    @Test
    void insurancePaymentFollowsPolicyScheduleFromStartDate() {
        InsurancePolicy policy = policy(LocalDate.of(2026, 1, 31), null, PaymentFrequency.MONTHLY);
        Deadline february =
                new Deadline(household, DeadlineType.INSURANCE_PAYMENT, LocalDate.of(2026, 2, 28), null, null, policy);

        // A febr. 28. utáni fizetés márc. 31., nem márc. 28.: a kezdőnapból számolunk, nem láncolva.
        assertThat(DeadlineRecurrence.nextDueDate(february, LocalDate.of(2026, 2, 27)))
                .contains(LocalDate.of(2026, 3, 31));
    }

    @Test
    void kgfbAnniversaryRecursYearly() {
        InsurancePolicy policy = policy(LocalDate.of(2024, 2, 29), null, PaymentFrequency.ANNUAL);
        Deadline anniversary =
                new Deadline(household, DeadlineType.KGFB_ANNIVERSARY, LocalDate.of(2027, 2, 28), null, null, policy);

        assertThat(DeadlineRecurrence.nextDueDate(anniversary, LocalDate.of(2027, 1, 10)))
                .contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void noNextDeadlineAfterPolicyEnds() {
        InsurancePolicy policy = policy(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), PaymentFrequency.QUARTERLY);
        Deadline lastPayment =
                new Deadline(household, DeadlineType.INSURANCE_PAYMENT, LocalDate.of(2026, 10, 1), null, null, policy);
        Deadline anniversary =
                new Deadline(household, DeadlineType.KGFB_ANNIVERSARY, LocalDate.of(2027, 1, 1), null, null, policy);

        assertThat(DeadlineRecurrence.nextDueDate(lastPayment, LocalDate.of(2026, 10, 1)))
                .isEmpty();
        assertThat(DeadlineRecurrence.nextDueDate(anniversary, LocalDate.of(2026, 12, 1)))
                .isEmpty();
    }

    private InsurancePolicy policy(LocalDate start, LocalDate end, PaymentFrequency frequency) {
        return new InsurancePolicy(
                vehicle,
                new Insurer("Biztosító"),
                PolicyType.KGFB,
                null,
                start,
                end,
                new BigDecimal("60000"),
                frequency);
    }
}
