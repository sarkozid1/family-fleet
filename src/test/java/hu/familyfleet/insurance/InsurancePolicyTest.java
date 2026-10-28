package hu.familyfleet.insurance;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.household.Household;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsurancePolicyTest {

    private final Vehicle vehicle = new Vehicle(new Household("Teszt család"), VehicleType.CAR, "Skoda", "Octavia", "ABC123");
    private final Insurer insurer = new Insurer("Teszt Biztosító");

    @ParameterizedTest(name = "kezdet {0}, vég {1}")
    @CsvSource({"2026-01-01, 2026-01-01", "2026-01-01, 2025-12-31"})
    void endDateMustBeAfterStartDate(LocalDate start, LocalDate end) {
        assertThatThrownBy(() -> policy(PolicyType.CASCO, start, end))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("záró dátum");
    }

    @Test
    void changeTermsKeepsDateRule() {
        InsurancePolicy policy = policy(PolicyType.CASCO, LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> policy.changeTerms(insurer, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1),
                        BigDecimal.TEN, PaymentFrequency.ANNUAL))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(policy.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void negativePremiumIsRejected() {
        assertThatThrownBy(() -> new InsurancePolicy(vehicle, insurer, PolicyType.KGFB,
                LocalDate.of(2026, 1, 1), new BigDecimal("-1"), PaymentFrequency.ANNUAL))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "2025-12-31, false",
        "2026-01-01, true",
        "2026-12-31, true",
        "2027-01-01, false"
    })
    void activeOnIsHalfOpenInterval(LocalDate day, boolean expected) {
        InsurancePolicy policy = policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        assertThat(policy.isActiveOn(day)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{0}, {1}) vs [{2}, {3}) -> {4}")
    @CsvSource({
        // átfedő időszakok
        "2026-01-01, 2027-01-01, 2026-06-01, 2027-06-01, true",
        "2026-01-01,           , 2030-01-01, 2031-01-01, true",
        "2026-01-01, 2027-01-01, 2025-01-01,           , true",
        "2026-01-01, 2027-01-01, 2026-03-01, 2026-04-01, true",
        // nem átfedő: egymás után következnek (a záró nap már a következőé)
        "2026-01-01, 2027-01-01, 2027-01-01,           , false",
        "2026-01-01, 2027-01-01, 2024-01-01, 2026-01-01, false",
        "2026-01-01, 2027-01-01, 2028-01-01, 2029-01-01, false"
    })
    void overlapDetection(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2, boolean expected) {
        InsurancePolicy first = policy(PolicyType.KGFB, start1, end1);
        InsurancePolicy second = policy(PolicyType.KGFB, start2, end2);

        assertThat(first.overlaps(second)).isEqualTo(expected);
        assertThat(second.overlaps(first)).isEqualTo(expected);
    }

    @Test
    void secondOverlappingKgfbIsRejected() {
        InsurancePolicy existing = policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null);
        InsurancePolicy candidate = policy(PolicyType.KGFB, LocalDate.of(2026, 10, 1), null);

        assertThatThrownBy(() -> candidate.ensureNoOverlappingKgfb(List.of(existing)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("KGFB");
    }

    @Test
    void followUpKgfbAfterPreviousEndsIsAllowed() {
        InsurancePolicy previous = policy(PolicyType.KGFB, LocalDate.of(2025, 3, 1), LocalDate.of(2026, 3, 1));
        InsurancePolicy next = policy(PolicyType.KGFB, LocalDate.of(2026, 3, 1), null);

        assertThatCode(() -> next.ensureNoOverlappingKgfb(List.of(previous))).doesNotThrowAnyException();
    }

    @Test
    void cascoMayOverlapWithKgfbAndOtherCasco() {
        InsurancePolicy kgfb = policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null);
        InsurancePolicy casco = policy(PolicyType.CASCO, LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> casco.ensureNoOverlappingKgfb(List.of(kgfb, casco))).doesNotThrowAnyException();
    }

    @Test
    void policyDoesNotConflictWithItself() {
        InsurancePolicy kgfb = policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> kgfb.ensureNoOverlappingKgfb(List.of(kgfb))).doesNotThrowAnyException();
    }

    private InsurancePolicy policy(PolicyType type, LocalDate start, LocalDate end) {
        return new InsurancePolicy(vehicle, insurer, type, null, start, end, new BigDecimal("50000"),
                PaymentFrequency.ANNUAL);
    }
}
