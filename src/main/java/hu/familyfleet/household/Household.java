package hu.familyfleet.household;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "household")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Household(String name) {
        rename(name);
    }

    /**
     * Ugyanaz a háztartás-e. Mentett entitásnál azonosító szerint hasonlít (lazy proxyval is működik,
     * mert a proxy ismeri az azonosítóját), mentetlennél referencia szerint.
     */
    public boolean isSameAs(Household other) {
        if (other == null) {
            return false;
        }
        if (id != null && other.getId() != null) {
            return id.equals(other.getId());
        }
        return this == other;
    }

    /** A név kötelező; a felesleges szóközöket levágjuk, hogy a listázás és rendezés egységes legyen. */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Household name must not be blank");
        }
        this.name = name.strip();
    }
}
