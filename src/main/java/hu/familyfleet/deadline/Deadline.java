package hu.familyfleet.deadline;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "deadline")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deadline extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private HouseholdMember member;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private InsurancePolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeadlineType type;

    @Setter
    @Column(length = 200)
    private String title;

    @Setter
    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineStatus status = DeadlineStatus.OPEN;

    private Instant completedAt;

    @Setter
    private String note;

    public Deadline(Household household, DeadlineType type, LocalDate dueDate) {
        this.household = household;
        this.type = type;
        this.dueDate = dueDate;
    }

    /** Üzleti művelet: a határidő teljesítése. Csak nyitott határidőn hívható. */
    public void markDone(Instant now) {
        if (status != DeadlineStatus.OPEN) {
            throw new IllegalStateException("Only OPEN deadlines can be completed, current: " + status);
        }
        this.status = DeadlineStatus.DONE;
        this.completedAt = now;
    }
}