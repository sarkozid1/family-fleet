package hu.familyfleet.deadline.dto;

import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineStatus;
import hu.familyfleet.deadline.DeadlineType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A {@code daysUntilDue} a kérés napjához képest (negatív = lejárt). */
public record DeadlineResponse(
        Long id,
        Long householdId,
        Long vehicleId,
        Long memberId,
        Long policyId,
        DeadlineType type,
        String title,
        LocalDate dueDate,
        DeadlineStatus status,
        long daysUntilDue,
        Instant completedAt,
        String note,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static DeadlineResponse from(Deadline deadline, LocalDate today) {
        return new DeadlineResponse(
                deadline.getId(),
                deadline.getHousehold().getId(),
                deadline.getVehicle() == null ? null : deadline.getVehicle().getId(),
                deadline.getMember() == null ? null : deadline.getMember().getId(),
                deadline.getPolicy() == null ? null : deadline.getPolicy().getId(),
                deadline.getType(),
                deadline.getTitle(),
                deadline.getDueDate(),
                deadline.getStatus(),
                ChronoUnit.DAYS.between(today, deadline.getDueDate()),
                deadline.getCompletedAt(),
                deadline.getNote(),
                deadline.getVersion(),
                deadline.getCreatedAt(),
                deadline.getUpdatedAt());
    }
}
