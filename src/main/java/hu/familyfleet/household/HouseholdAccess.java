package hu.familyfleet.household;

import hu.familyfleet.security.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Egy felhasználó hozzáférése egy háztartáshoz (ADR-0003). */
@Entity
@Table(name = "household_access")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public HouseholdAccess(AppUser user, Household household) {
        this.user = Objects.requireNonNull(user, "user");
        this.household = Objects.requireNonNull(household, "household");
    }
}
