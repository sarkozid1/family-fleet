package hu.familyfleet.deadline;

import hu.familyfleet.insurance.InsurancePolicy;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Ismétlődési szabályok: teljesítéskor mikor esedékes a következő határidő (ADR-0002).
 *
 * <p>Naptárhoz kötött típusoknál (adó, matrica, díj, évforduló, gumicsere) az előző
 * <em>esedékességből</em> számolunk, hogy a késve teljesítés ne tolja el a naptárat.
 * Elvégzéshez kötött típusoknál (műszaki vizsga, olajcsere) a <em>teljesítés napjából</em>,
 * mert az érvényesség az elvégzéstől fut.
 */
public final class DeadlineRecurrence {

    static final int TECHNICAL_INSPECTION_YEARS = 2;
    static final int OIL_CHANGE_YEARS = 1;
    static final int TIRE_CHANGE_MONTHS = 6;

    private DeadlineRecurrence() {}

    /** Üres, ha a típus nem ismétlődik, vagy a kötvény a következő esedékesség előtt lejár. */
    public static Optional<LocalDate> nextDueDate(Deadline deadline, LocalDate completedOn) {
        LocalDate due = deadline.getDueDate();
        return switch (deadline.getType()) {
            case VEHICLE_TAX -> Optional.of(DeadlineCalendar.nextVehicleTaxDate(due.plusDays(1)));
            case MOTORWAY_VIGNETTE -> Optional.of(DeadlineCalendar.vignetteExpiry(due));
            case TIRE_CHANGE -> Optional.of(due.plusMonths(TIRE_CHANGE_MONTHS));
            case TECHNICAL_INSPECTION -> Optional.of(completedOn.plusYears(TECHNICAL_INSPECTION_YEARS));
            case OIL_CHANGE -> Optional.of(completedOn.plusYears(OIL_CHANGE_YEARS));
            case INSURANCE_PAYMENT ->
                withinPolicy(
                        deadline.getPolicy(),
                        DeadlineCalendar.paymentAfter(
                                deadline.getPolicy().getStartDate(),
                                deadline.getPolicy().getPaymentFrequency(),
                                due));
            case KGFB_ANNIVERSARY ->
                withinPolicy(
                        deadline.getPolicy(),
                        DeadlineCalendar.nextAnniversary(deadline.getPolicy().getStartDate(), due));
            case DRIVING_LICENCE, OTHER -> Optional.empty();
        };
    }

    /** Lejárt kötvényhez nem generálunk újabb határidőt (a záró nap már nem tartozik a kötvényhez). */
    static Optional<LocalDate> withinPolicy(InsurancePolicy policy, LocalDate candidate) {
        return policy.getEndDate() == null || candidate.isBefore(policy.getEndDate())
                ? Optional.of(candidate)
                : Optional.empty();
    }
}
