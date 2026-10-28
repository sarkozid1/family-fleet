package hu.familyfleet.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.deadline.DeadlineType;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.InsurancePolicyRepository;
import hu.familyfleet.insurance.Insurer;
import hu.familyfleet.insurance.InsurerRepository;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class VehicleRepositoryTest {

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @Autowired
    private InsurancePolicyRepository policyRepository;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Test
    void savesVehicleWithNormalizedPlateAndAuditFields() {
        Household household = householdRepository.save(new Household("Teszt család"));

        Vehicle saved =
                vehicleRepository.saveAndFlush(new Vehicle(household, VehicleType.CAR, "Tesla", "Model 3", "abc-123"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLicensePlate()).isEqualTo("ABC123");
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicatePlateInSameHousehold() {
        Household household = householdRepository.save(new Household("Teszt család"));
        vehicleRepository.saveAndFlush(new Vehicle(household, VehicleType.CAR, "Tesla", "Model 3", "ABC123"));

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

        // Nem a tábla teljes tartalmát számoljuk: a közös tesztadatbázisban más tesztek járművei is lehetnek.
        assertThat(vehicleRepository.existsByHouseholdIdAndLicensePlate(mine.getId(), "ABC123"))
                .isTrue();
        assertThat(vehicleRepository.existsByHouseholdIdAndLicensePlate(partners.getId(), "ABC123"))
                .isTrue();
    }

    @Test
    void pagesVehiclesOfOneHouseholdSortedByPlate() {
        Household mine = householdRepository.save(new Household("Saját család"));
        Household other = householdRepository.save(new Household("Másik család"));
        vehicleRepository.save(new Vehicle(mine, VehicleType.CAR, "Skoda", "Octavia", "CCC333"));
        vehicleRepository.save(new Vehicle(mine, VehicleType.CAR, "Opel", "Astra", "AAA111"));
        vehicleRepository.save(new Vehicle(mine, VehicleType.MOTORCYCLE, "Honda", "CB500", "BBB222"));
        vehicleRepository.save(new Vehicle(other, VehicleType.CAR, "Ford", "Focus", "AAA000"));

        Page<Vehicle> firstPage =
                vehicleRepository.findByHouseholdId(mine.getId(), PageRequest.of(0, 2, Sort.by("licensePlate")));

        assertThat(firstPage.getContent()).extracting(Vehicle::getLicensePlate).containsExactly("AAA111", "BBB222");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void detectsDependentPoliciesAndDeadlines() {
        Household household = householdRepository.save(new Household("Teszt család"));
        Vehicle withPolicy =
                vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "AAA111"));
        Vehicle withDeadline =
                vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Opel", "Astra", "BBB222"));
        Vehicle free = vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Ford", "Focus", "CCC333"));
        Insurer insurer = insurerRepository.findAll().getFirst();
        policyRepository.save(new InsurancePolicy(
                withPolicy,
                insurer,
                PolicyType.KGFB,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("45000"),
                PaymentFrequency.ANNUAL));
        Deadline deadline = new Deadline(
                household, DeadlineType.TECHNICAL_INSPECTION, LocalDate.of(2027, 5, 1), withDeadline, null, null);
        deadlineRepository.save(deadline);

        assertThat(vehicleRepository.hasPolicies(withPolicy.getId())).isTrue();
        assertThat(vehicleRepository.hasDeadlines(withDeadline.getId())).isTrue();
        assertThat(vehicleRepository.hasPolicies(free.getId())).isFalse();
        assertThat(vehicleRepository.hasDeadlines(free.getId())).isFalse();
    }
}
