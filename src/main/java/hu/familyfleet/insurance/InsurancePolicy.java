package hu.familyfleet.insurance;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurer_id", nullable = false)
    private Insurer insurer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyType policyType;

    @Setter
    @Column(length = 50)
    private String policyNumber;

    @Setter
    @Column(nullable = false)
    private LocalDate startDate;

    @Setter
    private LocalDate endDate;

    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal annualPremium;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentFrequency paymentFrequency;

    public InsurancePolicy(Vehicle vehicle, Insurer insurer, PolicyType policyType,
                           LocalDate startDate, BigDecimal annualPremium,
                           PaymentFrequency paymentFrequency) {
        this.vehicle = vehicle;
        this.insurer = insurer;
        this.policyType = policyType;
        this.startDate = startDate;
        this.annualPremium = annualPremium;
        this.paymentFrequency = paymentFrequency;
    }
}