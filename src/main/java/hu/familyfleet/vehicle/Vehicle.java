package hu.familyfleet.vehicle;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private HouseholdMember owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType type;

    @Setter
    @Column(nullable = false, length = 50)
    private String brand;

    @Setter
    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 10)
    private String licensePlate;

    @Setter
    @Column(length = 17)
    private String vin;

    @Setter
    private Short productionYear;

    @Setter
    @Column(nullable = false)
    private Integer currentMileage = 0;

    public Vehicle(Household household, VehicleType type, String brand, String model, String licensePlate) {
        this.household = household;
        this.type = type;
        this.brand = brand;
        this.model = model;
        setLicensePlate(licensePlate);
    }

    /** Egységes formátum: nagybetű, szóköz és kötőjel nélkül ("abc-123" -> "ABC123"). */
    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate == null
                ? null
                : licensePlate.trim().toUpperCase().replaceAll("[\\s-]+", "");
    }
}