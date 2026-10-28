package hu.familyfleet.household;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "household_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(nullable = false, length = 100)
    private String name;

    private String email;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public HouseholdMember(Household household, String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Member name must not be blank");
        }
        this.household = Objects.requireNonNull(household, "household");
        this.name = name.strip();
        this.email = email == null || email.isBlank() ? null : email.strip().toLowerCase();
    }
}
