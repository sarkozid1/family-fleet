package hu.familyfleet.deadline;

import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import hu.familyfleet.vehicle.VehicleType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DeadlineRepositoryTest {

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Household household;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        household = householdRepository.save(new Household("Kovács család"));
        vehicle = vehicleRepository.save(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123"));
    }

    @Test
    void findOpenDueUntilReturnsOpenDeadlinesIncludingOverdueInDueDateOrder() {
        Deadline overdue = save(DeadlineType.OIL_CHANGE, LocalDate.of(2026, 10, 1));
        Deadline soon = save(DeadlineType.TECHNICAL_INSPECTION, LocalDate.of(2026, 11, 10));
        save(DeadlineType.TIRE_CHANGE, LocalDate.of(2026, 12, 31)); // túl késő
        Deadline done = save(DeadlineType.VEHICLE_TAX, LocalDate.of(2026, 11, 1));
        done.markDone(Instant.parse("2026-10-20T10:00:00Z"));
        Household otherHousehold = householdRepository.save(new Household("Másik család"));
        Vehicle otherVehicle = vehicleRepository.save(new Vehicle(otherHousehold, VehicleType.CAR, "Opel", "Astra", "XYZ987"));
        deadlineRepository.save(new Deadline(otherHousehold, DeadlineType.OIL_CHANGE, LocalDate.of(2026, 11, 1),
                otherVehicle, null, null));
        entityManager.flush();

        List<Deadline> result = deadlineRepository.findOpenDueUntil(household.getId(), LocalDate.of(2026, 11, 27));

        assertThat(result).containsExactly(overdue, soon);
    }

    @Test
    void specificationsCombineOnlyGivenFilters() {
        save(DeadlineType.OIL_CHANGE, LocalDate.of(2026, 3, 1));
        save(DeadlineType.VEHICLE_TAX, LocalDate.of(2026, 3, 15));
        save(DeadlineType.VEHICLE_TAX, LocalDate.of(2026, 9, 15));
        Deadline dismissed = save(DeadlineType.VEHICLE_TAX, LocalDate.of(2027, 3, 15));
        dismissed.dismiss();
        entityManager.flush();
        PageRequest page = PageRequest.of(0, 10, Sort.by("dueDate"));

        assertThat(deadlineRepository.findAll(
                DeadlineSpecifications.matching(household.getId(), null, null, null, null), page).getTotalElements())
                .isEqualTo(4);
        assertThat(deadlineRepository.findAll(
                DeadlineSpecifications.matching(household.getId(), DeadlineStatus.OPEN, DeadlineType.VEHICLE_TAX, null, null), page))
                .extracting(Deadline::getDueDate)
                .containsExactly(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 15));
        assertThat(deadlineRepository.findAll(DeadlineSpecifications.matching(
                        household.getId(), null, null, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 12, 31)), page))
                .extracting(Deadline::getDueDate)
                .containsExactly(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 15));
    }

    /**
     * Ugyanaz a lekérdezési alak, amit a {@code findOpenDueUntil} generál (literál 'OPEN'): ellenőrizzük,
     * hogy a részleges index illeszkedik rá. A seq scant letiltjuk, mert kevés sornál a tervező
     * egyébként jogosan a teljes táblát olvasná.
     */
    @Test
    void upcomingQueryShapeCanUsePartialIndex() {
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");

        List<String> plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT * FROM deadline
                WHERE household_id = 1 AND status = 'OPEN' AND due_date <= DATE '2026-12-01'
                ORDER BY due_date
                """, String.class);

        assertThat(String.join("\n", plan)).contains("idx_deadline_open_due");
    }

    @Test
    void databaseRequiresTypeSpecificLink() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO deadline (household_id, type, due_date) VALUES (?, 'TECHNICAL_INSPECTION', DATE '2026-12-01')",
                household.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_deadline_required_link");
    }

    @Test
    void databaseRequiresCompletedAtExactlyForDoneDeadlines() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO deadline (household_id, type, due_date, status) VALUES (?, 'OTHER', DATE '2026-12-01', 'DONE')",
                household.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_deadline_completed_at");
    }

    private Deadline save(DeadlineType type, LocalDate due) {
        return deadlineRepository.save(new Deadline(household, type, due, vehicle, null, null));
    }
}
