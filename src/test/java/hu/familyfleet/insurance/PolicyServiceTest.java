package hu.familyfleet.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdAccessGuard;
import hu.familyfleet.insurance.dto.PolicyCreateRequest;
import hu.familyfleet.insurance.dto.PolicyResponse;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import hu.familyfleet.vehicle.VehicleType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** A service üzleti folyamata Spring és adatbázis nélkül: a repository-k Mockito-mockok, az idő rögzített. */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Budapest"));

    @Mock
    private InsurancePolicyRepository policyRepository;

    @Mock
    private InsurerRepository insurerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HouseholdAccessGuard accessGuard;

    private PolicyService policyService;

    private final Vehicle vehicle =
            new Vehicle(new Household("Teszt család"), VehicleType.CAR, "Skoda", "Octavia", "ABC123");
    private final Insurer insurer = new Insurer("Teszt Biztosító");

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(
                policyRepository, insurerRepository, vehicleRepository, CLOCK, eventPublisher, accessGuard);
    }

    @Test
    void rejectsSecondActiveKgfbForSameVehicle() {
        given(vehicleRepository.findById(1L)).willReturn(Optional.of(vehicle));
        given(insurerRepository.findById(2L)).willReturn(Optional.of(insurer));
        given(policyRepository.findByVehicleIdAndPolicyType(1L, PolicyType.KGFB))
                .willReturn(List.of(new InsurancePolicy(
                        vehicle,
                        insurer,
                        PolicyType.KGFB,
                        LocalDate.of(2026, 1, 1),
                        new BigDecimal("48000"),
                        PaymentFrequency.ANNUAL)));

        assertThatThrownBy(() -> policyService.create(1L, kgfbRequest(LocalDate.of(2026, 11, 1))))
                .isInstanceOf(ConflictException.class);
        verify(policyRepository, never()).save(any());
    }

    @Test
    void createsKgfbWhenNoOtherKgfbExists() {
        given(vehicleRepository.findById(1L)).willReturn(Optional.of(vehicle));
        given(insurerRepository.findById(2L)).willReturn(Optional.of(insurer));
        given(policyRepository.findByVehicleIdAndPolicyType(1L, PolicyType.KGFB))
                .willReturn(List.of());
        given(policyRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PolicyResponse response = policyService.create(1L, kgfbRequest(LocalDate.of(2026, 10, 1)));

        assertThat(response.policyType()).isEqualTo(PolicyType.KGFB);
        assertThat(response.insurerName()).isEqualTo("Teszt Biztosító");
        assertThat(response.active())
                .as("2026-10-01-jén indult, a rögzített mai nap 2026-10-28")
                .isTrue();
        verify(eventPublisher).publishEvent(any(PolicyCreatedEvent.class));
    }

    @Test
    void unknownInsurerIsBadRequestNotNotFound() {
        given(vehicleRepository.findById(1L)).willReturn(Optional.of(vehicle));
        given(insurerRepository.findById(2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.create(1L, kgfbRequest(LocalDate.of(2026, 10, 1))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unknownVehicleIsNotFound() {
        given(vehicleRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.create(1L, kgfbRequest(LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static PolicyCreateRequest kgfbRequest(LocalDate start) {
        return new PolicyCreateRequest(
                2L, PolicyType.KGFB, "KGFB-1", start, null, new BigDecimal("52000"), PaymentFrequency.QUARTERLY);
    }
}
