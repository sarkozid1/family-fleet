package hu.familyfleet.deadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.Insurer;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeadlineTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final LocalDate DUE = LocalDate.of(2026, 11, 1);

    private final Household household = new Household("Kovács család");
    private final Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");
    private final HouseholdMember member = new HouseholdMember(household, "Anna", null);
    private final InsurancePolicy policy = new InsurancePolicy(
            vehicle,
            new Insurer("Biztosító"),
            PolicyType.KGFB,
            LocalDate.of(2026, 1, 1),
            new BigDecimal("50000"),
            PaymentFrequency.ANNUAL);

    @Test
    void markDoneSetsStatusAndCompletionTime() {
        Deadline deadline = vehicleDeadline(DeadlineType.TECHNICAL_INSPECTION);

        deadline.markDone(NOW);

        assertThat(deadline.getStatus()).isEqualTo(DeadlineStatus.DONE);
        assertThat(deadline.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotCompleteTwice() {
        Deadline deadline = vehicleDeadline(DeadlineType.VEHICLE_TAX);
        deadline.markDone(NOW);

        assertThatThrownBy(() -> deadline.markDone(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void dismissMovesOpenDeadlineToDismissedWithoutCompletionTime() {
        Deadline deadline = vehicleDeadline(DeadlineType.OIL_CHANGE);

        deadline.dismiss();

        assertThat(deadline.getStatus()).isEqualTo(DeadlineStatus.DISMISSED);
        assertThat(deadline.getCompletedAt()).isNull();
    }

    @Test
    void closedDeadlineCannotBeDismissedOrCompleted() {
        Deadline done = vehicleDeadline(DeadlineType.OIL_CHANGE);
        done.markDone(NOW);
        Deadline dismissed = vehicleDeadline(DeadlineType.OIL_CHANGE);
        dismissed.dismiss();

        assertThatThrownBy(done::dismiss).isInstanceOf(ConflictException.class);
        assertThatThrownBy(dismissed::dismiss).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> dismissed.markDone(NOW)).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = DeadlineType.class,
            names = {"TECHNICAL_INSPECTION", "OIL_CHANGE", "TIRE_CHANGE", "VEHICLE_TAX", "MOTORWAY_VIGNETTE"})
    void vehicleTypesRequireVehicle(DeadlineType type) {
        assertThatThrownBy(() -> new Deadline(household, type, DUE, null, member, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vehicleId");
        assertThat(new Deadline(household, type, DUE, vehicle, null, null).getVehicle())
                .isSameAs(vehicle);
    }

    @Test
    void drivingLicenceRequiresMember() {
        assertThatThrownBy(() -> new Deadline(household, DeadlineType.DRIVING_LICENCE, DUE, vehicle, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("memberId");
    }

    @ParameterizedTest
    @EnumSource(
            value = DeadlineType.class,
            names = {"INSURANCE_PAYMENT", "KGFB_ANNIVERSARY"})
    void policyTypesRequirePolicyAndInheritItsVehicle(DeadlineType type) {
        assertThatThrownBy(() -> new Deadline(household, type, DUE, vehicle, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("policyId");

        Deadline deadline = new Deadline(household, type, DUE, null, null, policy);
        assertThat(deadline.getVehicle()).isSameAs(vehicle);
    }

    @Test
    void otherNeedsNoLink() {
        assertThat(new Deadline(household, DeadlineType.OTHER, DUE, null, null, null).isOpen())
                .isTrue();
    }

    @Test
    void linkedItemsMustBelongToSameHousehold() {
        Household other = new Household("Nagy család");
        Vehicle foreignVehicle = new Vehicle(other, VehicleType.CAR, "Opel", "Astra", "XYZ987");
        HouseholdMember foreignMember = new HouseholdMember(other, "Béla", null);

        assertThatThrownBy(() ->
                        new Deadline(household, DeadlineType.TECHNICAL_INSPECTION, DUE, foreignVehicle, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Deadline(household, DeadlineType.DRIVING_LICENCE, DUE, null, foreignMember, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Deadline(other, DeadlineType.KGFB_ANNIVERSARY, DUE, null, null, policy))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void vehicleMustMatchPolicyVehicle() {
        Vehicle secondCar = new Vehicle(household, VehicleType.CAR, "Opel", "Astra", "XYZ987");

        assertThatThrownBy(() -> new Deadline(household, DeadlineType.INSURANCE_PAYMENT, DUE, secondCar, null, policy))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kötvény járművével");
    }

    private Deadline vehicleDeadline(DeadlineType type) {
        return new Deadline(household, type, DUE, vehicle, null, null);
    }
}
