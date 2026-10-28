package hu.familyfleet.deadline;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.deadline.dto.CompleteDeadlineRequest;
import hu.familyfleet.deadline.dto.CompleteDeadlineResponse;
import hu.familyfleet.deadline.dto.DeadlineCreateRequest;
import hu.familyfleet.deadline.dto.DeadlineResponse;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.insurance.InsurancePolicyRepository;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import hu.familyfleet.vehicle.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A határidő-service szabályai rögzített órával (2026-10-28, Budapest). A repository-k mockok,
 * a {@link DeadlineGenerator} valódi, mert tiszta számítás.
 */
@ExtendWith(MockitoExtension.class)
class DeadlineServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Budapest"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Mock
    private DeadlineRepository deadlineRepository;
    @Mock
    private HouseholdRepository householdRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private HouseholdMemberRepository memberRepository;
    @Mock
    private InsurancePolicyRepository policyRepository;

    private DeadlineService service;

    private final Household household = new Household("Kovács család");
    private final Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");

    @BeforeEach
    void setUp() {
        DeadlineGenerator generator = new DeadlineGenerator(deadlineRepository, vehicleRepository, policyRepository, CLOCK);
        service = new DeadlineService(deadlineRepository, householdRepository, vehicleRepository, memberRepository,
                policyRepository, generator, CLOCK);
    }

    @Test
    void vehicleTaxWithoutDueDateGetsNextTaxDateAndGeneratedTitle() {
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));
        given(vehicleRepository.findById(2L)).willReturn(Optional.of(vehicle));
        given(deadlineRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        DeadlineResponse response = service.create(1L,
                new DeadlineCreateRequest(DeadlineType.VEHICLE_TAX, null, null, null, 2L, null, null));

        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(response.title()).isEqualTo("Gépjárműadó – ABC123");
        assertThat(response.daysUntilDue()).isEqualTo(138);
    }

    @Test
    void clientTitleWinsOverGeneratedOne() {
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));
        given(vehicleRepository.findById(2L)).willReturn(Optional.of(vehicle));
        given(deadlineRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        DeadlineResponse response = service.create(1L, new DeadlineCreateRequest(
                DeadlineType.MOTORWAY_VIGNETTE, null, "Matrica a nyaraláshoz", null, 2L, null, null));

        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(response.title()).isEqualTo("Matrica a nyaraláshoz");
    }

    @Test
    void inspectionWithoutDueDateIsRejected() {
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));
        given(vehicleRepository.findById(2L)).willReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.create(1L, new DeadlineCreateRequest(
                        DeadlineType.TECHNICAL_INSPECTION, null, null, null, 2L, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dueDate");
    }

    @Test
    void unknownLinkedVehicleIsBadRequest() {
        given(householdRepository.findById(1L)).willReturn(Optional.of(household));
        given(vehicleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new DeadlineCreateRequest(
                        DeadlineType.OIL_CHANGE, TODAY, null, null, 99L, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("99");
    }

    @Test
    void unknownHouseholdIsNotFound() {
        given(householdRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new DeadlineCreateRequest(
                        DeadlineType.OTHER, TODAY, "x", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completingRecurringDeadlineCreatesNextOne() {
        Deadline tax = new Deadline(household, DeadlineType.VEHICLE_TAX, LocalDate.of(2026, 9, 15), vehicle, null, null);
        given(deadlineRepository.findById(5L)).willReturn(Optional.of(tax));
        given(deadlineRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CompleteDeadlineResponse response = service.complete(5L, null);

        assertThat(response.completed().status()).isEqualTo(DeadlineStatus.DONE);
        assertThat(response.completed().completedAt()).isEqualTo(CLOCK.instant());
        assertThat(response.next().dueDate()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(response.next().status()).isEqualTo(DeadlineStatus.OPEN);
    }

    @Test
    void explicitNextDueDateCreatesFollowUpEvenForNonRecurringType() {
        HouseholdMember anna = new HouseholdMember(household, "Anna", null);
        Deadline licence = new Deadline(household, DeadlineType.DRIVING_LICENCE, LocalDate.of(2026, 11, 20), null, anna, null);
        licence.describe("Anna jogosítványa lejár", null);
        given(deadlineRepository.findById(6L)).willReturn(Optional.of(licence));
        given(deadlineRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CompleteDeadlineResponse response = service.complete(6L, new CompleteDeadlineRequest(LocalDate.of(2036, 11, 20)));

        assertThat(response.next().dueDate()).isEqualTo(LocalDate.of(2036, 11, 20));
        assertThat(response.next().title()).isEqualTo("Anna jogosítványa lejár");
    }

    @Test
    void nonRecurringDeadlineWithoutNextDateCreatesNothing() {
        Deadline other = new Deadline(household, DeadlineType.OTHER, LocalDate.of(2026, 11, 1), null, null, null);
        given(deadlineRepository.findById(7L)).willReturn(Optional.of(other));

        CompleteDeadlineResponse response = service.complete(7L, new CompleteDeadlineRequest(null));

        assertThat(response.next()).isNull();
        verify(deadlineRepository, never()).save(any());
    }

    @Test
    void nextDueDateMustBeAfterCurrentDueDate() {
        Deadline oil = new Deadline(household, DeadlineType.OIL_CHANGE, LocalDate.of(2026, 11, 1), vehicle, null, null);
        given(deadlineRepository.findById(8L)).willReturn(Optional.of(oil));

        assertThatThrownBy(() -> service.complete(8L, new CompleteDeadlineRequest(LocalDate.of(2026, 11, 1))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(oil.isOpen()).isTrue();
    }

    @Test
    void upcomingLooksAheadFromToday() {
        given(householdRepository.existsById(1L)).willReturn(true);
        given(deadlineRepository.findOpenDueUntil(1L, LocalDate.of(2026, 11, 4))).willReturn(List.of(
                new Deadline(household, DeadlineType.OIL_CHANGE, LocalDate.of(2026, 10, 20), vehicle, null, null)));

        List<DeadlineResponse> upcoming = service.upcoming(1L, 7);

        assertThat(upcoming).singleElement()
                .satisfies(deadline -> assertThat(deadline.daysUntilDue()).as("lejárt").isEqualTo(-8));
    }

    @Test
    void searchRejectsInvertedDateRange() {
        given(householdRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> service.search(1L, null, null, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
