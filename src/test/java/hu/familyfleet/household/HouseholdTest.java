package hu.familyfleet.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HouseholdTest {

    @Test
    void renameStripsWhitespace() {
        Household household = new Household("Régi név");

        household.rename("  Kovács család  ");

        assertThat(household.getName()).isEqualTo("Kovács család");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsBlankName(String name) {
        Household household = new Household("Kovács család");

        assertThatThrownBy(() -> household.rename(name)).isInstanceOf(IllegalArgumentException.class);
        assertThat(household.getName()).isEqualTo("Kovács család");
    }

    @Test
    void memberEmailIsNormalizedAndBlankBecomesNull() {
        Household household = new Household("Kovács család");

        assertThat(new HouseholdMember(household, " Anna ", " Anna@Example.COM ").getEmail())
                .isEqualTo("anna@example.com");
        assertThat(new HouseholdMember(household, "Bence", "  ").getEmail()).isNull();
    }
}
