package hu.familyfleet.insurance;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Biztosító: törzsadat, amelyet minden háztartás közösen használ. */
@Entity
@Table(name = "insurer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insurer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    private String website;

    public Insurer(String name) {
        this(name, null, null);
    }

    public Insurer(String name, String phone, String website) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Insurer name must not be blank");
        }
        this.name = name.strip();
        this.phone = blankToNull(phone);
        this.website = blankToNull(website);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
