package hu.familyfleet.deadline.dto;

import hu.familyfleet.deadline.DeadlineType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Új határidő. A {@code dueDate} elhagyható azoknál a típusoknál, amelyeknél számítható
 * (adó, matrica, KGFB-évforduló, díjfizetés – lásd ADR-0002). Hogy melyik kapcsolat kötelező,
 * az a típustól függ; ezt az entitás ellenőrzi.
 */
public record DeadlineCreateRequest(
        @NotNull DeadlineType type,
        LocalDate dueDate,
        @Size(max = 200) String title,
        @Size(max = 2000) String note,
        Long vehicleId,
        Long memberId,
        Long policyId) {}
