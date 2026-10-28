package hu.familyfleet.deadline;

import hu.familyfleet.insurance.PaymentFrequency;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;

/**
 * Tiszta dátumszámítások a határidők generálásához (ADR-0002). Nincs benne állapot és nincs
 * benne "most": a mai napot mindig a hívó adja át (az injektált {@code Clock}-ból), így minden
 * szabály determinisztikusan, Spring nélkül tesztelhető.
 */
public final class DeadlineCalendar {

    static final MonthDay VEHICLE_TAX_SPRING = MonthDay.of(3, 15);
    static final MonthDay VEHICLE_TAX_AUTUMN = MonthDay.of(9, 15);
    static final MonthDay VIGNETTE_EXPIRY = MonthDay.of(1, 31);
    static final int KGFB_CANCELLATION_DAYS = 30;

    private DeadlineCalendar() {}

    /** A gépjárműadó következő esedékessége (márc. 15. vagy szept. 15.) az adott napon vagy utána. */
    public static LocalDate nextVehicleTaxDate(LocalDate onOrAfter) {
        LocalDate spring = VEHICLE_TAX_SPRING.atYear(onOrAfter.getYear());
        if (!onOrAfter.isAfter(spring)) {
            return spring;
        }
        LocalDate autumn = VEHICLE_TAX_AUTUMN.atYear(onOrAfter.getYear());
        if (!onOrAfter.isAfter(autumn)) {
            return autumn;
        }
        return VEHICLE_TAX_SPRING.atYear(onOrAfter.getYear() + 1);
    }

    /** Az éves (országos) autópálya-matrica a vásárlás évét követő év január 31-éig érvényes. */
    public static LocalDate vignetteExpiry(LocalDate purchaseDate) {
        return VIGNETTE_EXPIRY.atYear(purchaseDate.getYear() + 1);
    }

    /**
     * A kötvény következő évfordulója, szigorúan az adott nap után. Mindig a kezdő dátumból
     * számolunk ({@code start.plusYears(n)}), nem az előző évfordulóból: így a február 29-i
     * kezdésű kötvény nem "csúszik el" véglegesen február 28-ra (szökőévben visszaáll 29-re).
     */
    public static LocalDate nextAnniversary(LocalDate policyStart, LocalDate after) {
        long years = Math.max(1, ChronoUnit.YEARS.between(policyStart, after));
        LocalDate candidate = policyStart.plusYears(years);
        while (!candidate.isAfter(after)) {
            candidate = policyStart.plusYears(++years);
        }
        return candidate;
    }

    /** A KGFB az évforduló előtt legalább 30 nappal mondható fel: ez az utolsó nap. */
    public static LocalDate kgfbCancellationDeadline(LocalDate anniversary) {
        return anniversary.minusDays(KGFB_CANCELLATION_DAYS);
    }

    /**
     * A következő díjfizetés az adott napon vagy utána. A fizetési napok a kezdő dátumtól
     * {@code k * gyakoriság} hónapra esnek; a hónap végi kezdésnél ({@code jan. 31.}) a rövidebb
     * hónapokban a hónap utolsó napja lesz (febr. 28/29.), a hosszabbakban visszaáll 31-re.
     */
    public static LocalDate nextPaymentDate(LocalDate policyStart, PaymentFrequency frequency, LocalDate onOrAfter) {
        int step = frequency.monthsBetweenPayments();
        long periods = Math.max(0, ChronoUnit.MONTHS.between(policyStart, onOrAfter) / step);
        LocalDate candidate = policyStart.plusMonths(periods * step);
        while (candidate.isBefore(onOrAfter)) {
            candidate = policyStart.plusMonths(++periods * step);
        }
        return candidate;
    }

    /** Az előző esedékesség utáni díjfizetési nap. */
    public static LocalDate paymentAfter(LocalDate policyStart, PaymentFrequency frequency, LocalDate previousDue) {
        return nextPaymentDate(policyStart, frequency, previousDue.plusDays(1));
    }
}
