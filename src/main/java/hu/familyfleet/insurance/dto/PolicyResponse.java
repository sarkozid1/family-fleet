package hu.familyfleet.insurance.dto;

import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Az {@code active} a kérés napjára számított érték (injektált {@code Clock} alapján). */
public record PolicyResponse(
        Long id,
        Long vehicleId,
        Long insurerId,
        String insurerName,
        PolicyType policyType,
        String policyNumber,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal annualPremium,
        PaymentFrequency paymentFrequency,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static PolicyResponse from(InsurancePolicy policy, LocalDate today) {
        return new PolicyResponse(
                policy.getId(),
                policy.getVehicle().getId(),
                policy.getInsurer().getId(),
                policy.getInsurer().getName(),
                policy.getPolicyType(),
                policy.getPolicyNumber(),
                policy.getStartDate(),
                policy.getEndDate(),
                policy.getAnnualPremium(),
                policy.getPaymentFrequency(),
                policy.isActiveOn(today),
                policy.getVersion(),
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }
}
