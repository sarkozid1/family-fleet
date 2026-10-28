package hu.familyfleet.insurance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hu.familyfleet.insurance.PaymentFrequency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Kötvény módosítása. A típus és a jármű nem módosítható; a {@code version} az optimista zároláshoz kell. */
public record PolicyUpdateRequest(
        @NotNull Long insurerId,
        @Size(max = 50) String policyNumber,
        @NotNull LocalDate startDate,
        LocalDate endDate,

        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal annualPremium,

        @NotNull PaymentFrequency paymentFrequency,
        @NotNull Long version) {

    @JsonIgnore
    @AssertTrue(message = "a záró dátumnak a kezdő dátum után kell lennie")
    public boolean isEndDateAfterStartDate() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
