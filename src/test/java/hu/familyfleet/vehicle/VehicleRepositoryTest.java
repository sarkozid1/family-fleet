package hu.familyfleet.vehicle;

import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class VehicleRepositoryTest {

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void savesVehicleWithNormalizedPlateAndAuditFields() {
        Household household = householdRepository.save(new Household("Teszt család"));

        Vehicle saved = vehicleRepository.saveAndFlush(
                new Vehicle(household, VehicleType.CAR, "Tesla", "Model 3", "abc-123"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLicensePlate()).isEqualTo("ABC123");
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicatePlateInSameHousehold() {
        Household household = householdRepository.save(new Household("Teszt család"));
        vehicleRepository.saveAndFlush(
                new Vehicle(household, VehicleType.CAR, "Tesla", "Model 3", "ABC123"));

        assertThatThrownBy(() -> vehicleRepository.saveAndFlush(
                new Vehicle(household, VehicleType.MOTORCYCLE, "Yamaha", "MT-07", "abc 123")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSamePlateInDifferentHouseholds() {
        Household mine = householdRepository.save(new Household("Saját család"));
        Household partners = householdRepository.save(new Household("Párom családja"));

        vehicleRepository.saveAndFlush(new Vehicle(mine, VehicleType.CAR, "Skoda", "Octavia", "ABC123"));
        vehicleRepository.saveAndFlush(new Vehicle(partners, VehicleType.CAR, "Skoda", "Octavia", "ABC123"));

        assertThat(vehicleRepository.count()).isEqualTo(2);
    }
}
