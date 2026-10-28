package hu.familyfleet.deadline;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private HouseholdMember member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private InsurancePolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeadlineType type;

    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineStatus status = DeadlineStatus.OPEN;

    private Instant completedAt;

    private String note;

    /**
     * Új, nyitott határidő. A kapcsolatok érvényességét itt ellenőrizzük, így érvénytelen
     * határidő nem is jöhet létre:
     * <ul>
     *   <li>a típus megköveteli a megfelelő kapcsolatot (pl. műszaki vizsga → jármű),</li>
     *   <li>minden kapcsolt elem ugyanahhoz a háztartáshoz tartozik,</li>
     *   <li>kötvénynél a jármű a kötvény járműve (ha nincs megadva, onnan vesszük).</li>
     * </ul>
     */
    public Deadline(Household household, DeadlineType type, LocalDate dueDate,
                    Vehicle vehicle, HouseholdMember member, InsurancePolicy policy) {
        this.household = Objects.requireNonNull(household, "household");
        this.type = Objects.requireNonNull(type, "type");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.policy = policy;
        this.vehicle = resolveVehicle(vehicle, policy);
        this.member = member;
        ensureRequiredLink();
        ensureSameHousehold();
    }

    public void describe(String title, String note) {
        this.title = title == null || title.isBlank() ? null : title.strip();
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    public boolean isOpen() {
        return status == DeadlineStatus.OPEN;
    }

    /** Üzleti művelet: a határidő teljesítése. Csak nyitott határidőn hívható. */
    public void markDone(Instant now) {
        ensureOpen("teljesíteni");
        this.status = DeadlineStatus.DONE;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    /** Üzleti művelet: a határidő elvetése (pl. eladott autó műszakija). Csak nyitott határidőn hívható. */
    public void dismiss() {
        ensureOpen("elvetni");
        this.status = DeadlineStatus.DISMISSED;
    }

    private void ensureOpen(String action) {
        if (!isOpen()) {
            throw new ConflictException("Csak nyitott határidőt lehet %s, ez a határidő már %s állapotú."
                    .formatted(action, status));
        }
    }

    private static Vehicle resolveVehicle(Vehicle vehicle, InsurancePolicy policy) {
        if (policy == null) {
            return vehicle;
        }
        Vehicle policyVehicle = policy.getVehicle();
        if (vehicle != null && !sameVehicle(vehicle, policyVehicle)) {
            throw new BusinessRuleException("A megadott jármű nem egyezik a kötvény járművével.");
        }
        return policyVehicle;
    }

    private void ensureRequiredLink() {
        boolean present = switch (type.requiredLink()) {
            case VEHICLE -> vehicle != null;
            case MEMBER -> member != null;
            case POLICY -> policy != null;
            case NONE -> true;
        };
        if (!present) {
            throw new BusinessRuleException("A(z) %s típusú határidőhöz kötelező megadni: %s."
                    .formatted(type, linkName(type.requiredLink())));
        }
    }

    private void ensureSameHousehold() {
        if (vehicle != null && !household.isSameAs(vehicle.getHousehold())) {
            throw new BusinessRuleException("A jármű nem ehhez a háztartáshoz tartozik.");
        }
        if (member != null && !household.isSameAs(member.getHousehold())) {
            throw new BusinessRuleException("A családtag nem ehhez a háztartáshoz tartozik.");
        }
    }

    private static boolean sameVehicle(Vehicle a, Vehicle b) {
        if (a.getId() != null && b.getId() != null) {
            return a.getId().equals(b.getId());
        }
        return a == b;
    }

    private static String linkName(DeadlineType.RequiredLink link) {
        return switch (link) {
            case VEHICLE -> "jármű (vehicleId)";
            case MEMBER -> "családtag (memberId)";
            case POLICY -> "kötvény (policyId)";
            case NONE -> "semmi";
        };
    }
}
