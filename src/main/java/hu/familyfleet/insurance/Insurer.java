package hu.familyfleet.insurance;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "insurer")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insurer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    private String website;

    public Insurer(String name) {
        this.name = name;
    }
}