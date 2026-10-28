package hu.familyfleet.vehicle;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private final Household household = new Household("Kovács család");

    @ParameterizedTest
    @CsvSource({
        "abc-123,   ABC123",
        "' ABC 123 ', ABC123",
        "aa-bc-123, AABC123",
        "AA BC-123, AABC123"
    })
    void normalizesLicensePlate(String input, String expected) {
        assertThat(new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", input).getLicensePlate())
                .isEqualTo(expected);
    }

    @Test
    void mileageCanIncreaseOrStayTheSame() {
        Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");

        vehicle.updateMileage(120_000);
        vehicle.updateMileage(120_000);

        assertThat(vehicle.getCurrentMileage()).isEqualTo(120_000);
    }

    @Test
    void mileageCannotDecrease() {
        Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");
        vehicle.updateMileage(120_000);

        assertThatThrownBy(() -> vehicle.updateMileage(119_999))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("120000")
                .hasMessageContaining("119999");
        assertThat(vehicle.getCurrentMileage()).isEqualTo(120_000);
    }

    @Test
    void mileageCannotBeNegative() {
        Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");

        assertThatThrownBy(() -> vehicle.updateMileage(-1)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ownerMustBelongToSameHousehold() {
        Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");
        HouseholdMember stranger = new HouseholdMember(new Household("Nagy család"), "Béla", null);

        assertThatThrownBy(() -> vehicle.assignOwner(stranger)).isInstanceOf(BusinessRuleException.class);
        assertThat(vehicle.getOwner()).isNull();
    }

    @Test
    void ownerFromSameHouseholdCanBeAssignedAndRemoved() {
        Vehicle vehicle = new Vehicle(household, VehicleType.CAR, "Skoda", "Octavia", "ABC123");
        HouseholdMember anna = new HouseholdMember(household, "Anna", null);

        vehicle.assignOwner(anna);
        assertThat(vehicle.getOwner()).isSameAs(anna);

        vehicle.assignOwner(null);
        assertThat(vehicle.getOwner()).isNull();
    }
}
