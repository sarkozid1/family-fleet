package hu.familyfleet.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.common.exception.ConstraintNames;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import hu.familyfleet.vehicle.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** A V3-migráció megkötései valódi PostgreSQL-en: ezek a szabályok a kód megkerülésével sem sérülhetnek. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class InsurancePolicyRepositoryTest {

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @Autowired
    private InsurancePolicyRepository policyRepository;

    private Vehicle vehicle;
    private Insurer insurer;

    @BeforeEach
    void setUp() {
        Household household = householdRepository.save(new Household("Teszt család"));
        vehicle = vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123"));
        insurer = insurerRepository.findAll().getFirst();
    }

    @Test
    void databaseRejectsOverlappingKgfbWithNamedConstraint() {
        policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null));

        assertThatThrownBy(() -> policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ex_policy_kgfb_no_overlap");
    }

    /** A hibakezelő a megkötés neve alapján ad érthető üzenetet: ellenőrizzük, hogy a valódi hibából kinyerhető. */
    @Test
    void constraintNameCanBeExtractedFromRealExclusionViolation() {
        policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null));

        assertThatThrownBy(() -> policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2026, 6, 1), null)))
                .satisfies(ex -> assertThat(ConstraintNames.extract(ex)).contains("ex_policy_kgfb_no_overlap"));
    }

    @Test
    void databaseAllowsConsecutiveKgfbAndOverlappingCasco() {
        policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)));
        policyRepository.saveAndFlush(policy(PolicyType.KGFB, LocalDate.of(2026, 1, 1), null));
        policyRepository.saveAndFlush(policy(PolicyType.CASCO, LocalDate.of(2026, 1, 1), null));
        policyRepository.saveAndFlush(policy(PolicyType.CASCO, LocalDate.of(2026, 2, 1), null));

        assertThat(policyRepository.findByVehicleIdOrderByStartDateDesc(vehicle.getId()))
                .hasSize(4)
                .first()
                .extracting(InsurancePolicy::getStartDate)
                .isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void insurerNameIsUniqueWithRenamedConstraint() {
        assertThatThrownBy(() -> insurerRepository.saveAndFlush(new Insurer(insurer.getName())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(ConstraintNames.extract(ex)).contains("uq_insurer_name"));
    }

    private InsurancePolicy policy(PolicyType type, LocalDate start, LocalDate end) {
        return new InsurancePolicy(
                vehicle, insurer, type, null, start, end, new BigDecimal("50000"), PaymentFrequency.ANNUAL);
    }
}
