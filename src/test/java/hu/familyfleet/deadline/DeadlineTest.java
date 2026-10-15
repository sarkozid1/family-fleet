package hu.familyfleet.deadline;

import hu.familyfleet.household.Household;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlineTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void markDoneSetsStatusAndCompletionTime() {
        Deadline deadline = new Deadline(new Household("Teszt"),
                DeadlineType.TECHNICAL_INSPECTION, LocalDate.of(2026, 11, 1));

        deadline.markDone(NOW);

        assertThat(deadline.getStatus()).isEqualTo(DeadlineStatus.DONE);
        assertThat(deadline.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotCompleteTwice() {
        Deadline deadline = new Deadline(new Household("Teszt"),
                DeadlineType.VEHICLE_TAX, LocalDate.of(2027, 3, 15));
        deadline.markDone(NOW);

        assertThatThrownBy(() -> deadline.markDone(NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
