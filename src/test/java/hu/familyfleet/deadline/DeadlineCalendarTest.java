package hu.familyfleet.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import hu.familyfleet.insurance.PaymentFrequency;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A dátumszabályok határesetei: évváltás, hónap vége, szökőév. Spring és adatbázis nélkül. */
class DeadlineCalendarTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "2026-01-01, 2026-03-15",
        "2026-03-14, 2026-03-15",
        "2026-03-15, 2026-03-15", // aznap esedékes: még ez a következő
        "2026-03-16, 2026-09-15",
        "2026-09-15, 2026-09-15",
        "2026-09-16, 2027-03-15", // évváltás
        "2026-12-31, 2027-03-15"
    })
    void nextVehicleTaxDate(LocalDate onOrAfter, LocalDate expected) {
        assertThat(DeadlineCalendar.nextVehicleTaxDate(onOrAfter)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "vásárlás {0} -> lejárat {1}")
    @CsvSource({"2026-01-01, 2027-01-31", "2026-10-28, 2027-01-31", "2026-12-31, 2027-01-31", "2028-02-29, 2029-01-31"})
    void vignetteExpiresOnJanuary31stOfNextYear(LocalDate purchase, LocalDate expected) {
        assertThat(DeadlineCalendar.vignetteExpiry(purchase)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "kezdet {0}, ma {1} -> {2}")
    @CsvSource({
        "2026-03-01, 2026-10-28, 2027-03-01",
        "2026-03-01, 2027-02-28, 2027-03-01",
        "2026-03-01, 2027-03-01, 2028-03-01", // az aznapi évforduló már nem "következő"
        "2027-05-01, 2026-10-28, 2028-05-01", // jövőbeli kezdés: az első évforduló
        "2020-06-15, 2026-10-28, 2027-06-15", // régi kötvény
        // szökőnapi kezdés: nem szökőévben febr. 28., szökőévben visszaáll febr. 29-re
        "2024-02-29, 2024-10-01, 2025-02-28",
        "2024-02-29, 2025-02-28, 2026-02-28",
        "2024-02-29, 2027-03-01, 2028-02-29"
    })
    void nextAnniversary(LocalDate start, LocalDate today, LocalDate expected) {
        assertThat(DeadlineCalendar.nextAnniversary(start, today)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "évforduló {0} -> felmondás {1}")
    @CsvSource({
        "2027-03-01, 2027-01-30",
        "2028-03-01, 2028-01-31", // szökőév: februárnak 29 napja van
        "2027-01-15, 2026-12-16" // évváltáson át
    })
    void kgfbCancellationDeadlineIs30DaysBeforeAnniversary(LocalDate anniversary, LocalDate expected) {
        assertThat(DeadlineCalendar.kgfbCancellationDeadline(anniversary)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "kezdet {0}, {1}, ettől {2} -> {3}")
    @CsvSource({
        "2026-03-01, ANNUAL,      2026-10-28, 2027-03-01",
        "2026-03-01, SEMI_ANNUAL, 2026-10-28, 2027-03-01",
        "2026-03-01, QUARTERLY,   2026-10-28, 2026-12-01",
        "2026-03-01, MONTHLY,     2026-10-28, 2026-11-01",
        "2026-03-01, QUARTERLY,   2026-09-01, 2026-09-01", // aznap esedékes
        "2027-01-15, MONTHLY,     2026-10-28, 2027-01-15", // jövőbeli kezdés: az első fizetés a kezdőnap
        // hónap végi kezdés: a rövid hónapokban az utolsó nap, utána visszaáll 31-re
        "2026-01-31, MONTHLY,     2026-02-01, 2026-02-28",
        "2026-01-31, MONTHLY,     2026-03-01, 2026-03-31",
        "2026-01-31, MONTHLY,     2026-04-01, 2026-04-30",
        "2028-01-31, MONTHLY,     2028-02-01, 2028-02-29", // szökőév
        "2025-08-31, SEMI_ANNUAL, 2025-09-01, 2026-02-28"
    })
    void nextPaymentDate(LocalDate start, PaymentFrequency frequency, LocalDate onOrAfter, LocalDate expected) {
        assertThat(DeadlineCalendar.nextPaymentDate(start, frequency, onOrAfter))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "kezdet {0}, {1}, előző {2} -> {3}")
    @CsvSource({
        "2026-01-31, MONTHLY,   2026-02-28, 2026-03-31",
        "2026-03-01, QUARTERLY, 2026-12-01, 2027-03-01",
        "2026-03-01, ANNUAL,    2027-03-01, 2028-03-01"
    })
    void paymentAfterPreviousDue(LocalDate start, PaymentFrequency frequency, LocalDate previous, LocalDate expected) {
        assertThat(DeadlineCalendar.paymentAfter(start, frequency, previous)).isEqualTo(expected);
    }
}
