package hu.familyfleet.insurance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Új kötvény. A díj a {@code NUMERIC(12,2)} oszlophoz igazodik: legfeljebb 10 egész és 2 tizedesjegy. */
public record PolicyCreateRequest(
        @NotNull Long insurerId,
        @NotNull PolicyType policyType,
        @Size(max = 50) String policyNumber,
        @NotNull LocalDate startDate,
        LocalDate endDate,

        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal annualPremium,

        @NotNull PaymentFrequency paymentFrequency) {

    /** Mezőnkénti hibaüzenethez már a DTO-n is ellenőrizzük; a végső szabály az entitásban van. */
    @JsonIgnore
    @AssertTrue(message = "a záró dátumnak a kezdő dátum után kell lennie")
    public boolean isEndDateAfterStartDate() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
