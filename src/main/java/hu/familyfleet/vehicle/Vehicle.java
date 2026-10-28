package hu.familyfleet.vehicle;

import hu.familyfleet.common.AuditableEntity;
import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private HouseholdMember owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType type;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 10)
    private String licensePlate;

    @Column(length = 17)
    private String vin;

    private Short productionYear;

    @Column(nullable = false)
    private Integer currentMileage = 0;

    public Vehicle(Household household, VehicleType type, String brand, String model, String licensePlate) {
        this.household = Objects.requireNonNull(household, "household");
        updateDetails(type, brand, model, null, null);
        changeLicensePlate(licensePlate);
    }

    /** Egységes formátum: nagybetű, szóköz és kötőjel nélkül ("abc-123" -> "ABC123"). */
    public static String normalizePlate(String licensePlate) {
        return licensePlate == null ? null : licensePlate.trim().toUpperCase().replaceAll("[\\s-]+", "");
    }

    public void changeLicensePlate(String licensePlate) {
        String normalized = normalizePlate(licensePlate);
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessRuleException("A rendszám megadása kötelező.");
        }
        this.licensePlate = normalized;
    }

    public void updateDetails(VehicleType type, String brand, String model, String vin, Short productionYear) {
        this.type = Objects.requireNonNull(type, "type");
        this.brand = Objects.requireNonNull(brand, "brand").strip();
        this.model = Objects.requireNonNull(model, "model").strip();
        this.vin = vin == null || vin.isBlank() ? null : vin.strip().toUpperCase();
        this.productionYear = productionYear;
    }

    /**
     * A km-óra állása csak nőhet (vagy maradhat). A visszatekert óra tipikus csalás vagy elírás,
     * ezért az entitás nem engedi, akárhonnan hívják.
     */
    public void updateMileage(int newMileage) {
        if (newMileage < 0) {
            throw new BusinessRuleException("A km-óra állása nem lehet negatív.");
        }
        if (newMileage < currentMileage) {
            throw new BusinessRuleException(
                    "A km-óra állása nem csökkenhet (jelenlegi: %d km, új: %d km).".formatted(currentMileage, newMileage));
        }
        this.currentMileage = newMileage;
    }

    /** Tulajdonos csak ugyanannak a háztartásnak a tagja lehet; {@code null} = nincs tulajdonos. */
    public void assignOwner(HouseholdMember newOwner) {
        if (newOwner != null && !household.isSameAs(newOwner.getHousehold())) {
            throw new BusinessRuleException("A tulajdonos csak a jármű háztartásának tagja lehet.");
        }
        this.owner = newOwner;
    }
}
