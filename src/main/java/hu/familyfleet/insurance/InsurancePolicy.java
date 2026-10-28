package hu.familyfleet.insurance;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.vehicle.Vehicle;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "insurance_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsurancePolicy extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurer_id", nullable = false)
    private Insurer insurer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyType policyType;

    @Column(length = 50)
    private String policyNumber;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal annualPremium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentFrequency paymentFrequency;

    public InsurancePolicy(
            Vehicle vehicle,
            Insurer insurer,
            PolicyType policyType,
            LocalDate startDate,
            BigDecimal annualPremium,
            PaymentFrequency paymentFrequency) {
        this(vehicle, insurer, policyType, null, startDate, null, annualPremium, paymentFrequency);
    }

    public InsurancePolicy(
            Vehicle vehicle,
            Insurer insurer,
            PolicyType policyType,
            String policyNumber,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal annualPremium,
            PaymentFrequency paymentFrequency) {
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
        this.policyType = Objects.requireNonNull(policyType, "policyType");
        changeTerms(insurer, policyNumber, startDate, endDate, annualPremium, paymentFrequency);
    }

    /**
     * A kötvény feltételeinek módosítása. A típus (KGFB/CASCO) és a jármű nem változhat: az már
     * egy másik kötvény lenne.
     */
    public void changeTerms(
            Insurer insurer,
            String policyNumber,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal annualPremium,
            PaymentFrequency paymentFrequency) {
        Objects.requireNonNull(startDate, "startDate");
        if (endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessRuleException(
                    "A kötvény záró dátumának (%s) a kezdő dátum (%s) után kell lennie.".formatted(endDate, startDate));
        }
        if (annualPremium == null || annualPremium.signum() < 0) {
            throw new BusinessRuleException("Az éves díj nem lehet negatív.");
        }
        this.insurer = Objects.requireNonNull(insurer, "insurer");
        this.policyNumber = policyNumber == null || policyNumber.isBlank() ? null : policyNumber.strip();
        this.startDate = startDate;
        this.endDate = endDate;
        this.annualPremium = annualPremium;
        this.paymentFrequency = Objects.requireNonNull(paymentFrequency, "paymentFrequency");
    }

    /** Érvényes-e az adott napon. Az időszak félig nyitott: a záró napon már nem. */
    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || date.isBefore(endDate));
    }

    /** Két [start, end) időszak akkor fed át, ha mindkettő a másik vége előtt kezdődik (null vég = végtelen). */
    public boolean overlaps(InsurancePolicy other) {
        boolean startsBeforeOtherEnds = other.endDate == null || startDate.isBefore(other.endDate);
        boolean otherStartsBeforeThisEnds = endDate == null || other.startDate.isBefore(endDate);
        return startsBeforeOtherEnds && otherStartsBeforeThisEnds;
    }

    /**
     * Üzleti szabály: egy járműnek egyszerre csak egy érvényes KGFB-je lehet. CASCO-ból
     * lehet több. A kapott lista a jármű többi kötvénye (önmagát kihagyjuk, hogy módosításnál is működjön).
     */
    public void ensureNoOverlappingKgfb(Collection<InsurancePolicy> otherPoliciesOfVehicle) {
        if (policyType != PolicyType.KGFB) {
            return;
        }
        otherPoliciesOfVehicle.stream()
                .filter(other -> other != this && (id == null || !id.equals(other.getId())))
                .filter(other -> other.getPolicyType() == PolicyType.KGFB)
                .filter(this::overlaps)
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(
                            "A járműnek már van érvényes KGFB-kötvénye ebben az időszakban (kötvény id: %d, %s – %s)."
                                    .formatted(
                                            other.getId(),
                                            other.getStartDate(),
                                            other.getEndDate() == null ? "határozatlan" : other.getEndDate()));
                });
    }
}
