package hu.familyfleet.security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Bejelentkezni képes felhasználó. Nem azonos a családtaggal ({@code HouseholdMember}): egy
 * családtagnak nem kell fiók (pl. gyerek), egy felhasználó pedig több háztartáshoz is férhet.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** Soha nem a jelszó, csak a {@code PasswordEncoder} által előállított hash. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public AppUser(String email, String passwordHash, String displayName, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = Objects.requireNonNull(displayName, "displayName").strip();
        this.role = Objects.requireNonNull(role, "role");
    }

    /** Az e-mail kis- és nagybetűtől független azonosító: mindig kisbetűsen tároljuk és keressük. */
    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
    }
}
