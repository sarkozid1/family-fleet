package hu.familyfleet.household;

import hu.familyfleet.security.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Meghívó egy még nem regisztrált e-mail-címre. Regisztrációkor hozzáférés lesz belőle
 * ({@link HouseholdAccess}), a meghívó pedig törlődik.
 */
@Entity
@Table(name = "household_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private AppUser invitedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Az e-mail-címet a hívó normalizálja ({@code AppUser.normalizeEmail}); a DB CHECK is kisbetűt vár. */
    public HouseholdInvitation(Household household, String normalizedEmail, AppUser invitedBy) {
        this.household = Objects.requireNonNull(household, "household");
        this.email = Objects.requireNonNull(normalizedEmail, "email");
        this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy");
    }
}
