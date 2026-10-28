package hu.familyfleet.deadline;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dinamikus szűrés Specification-ökkel: csak a megadott feltételek kerülnek a WHERE-be.
 * A "{@code :param is null or ...}" JPQL-trükk helyett ez típusbiztos, és nem okoz
 * PostgreSQL-oldali típus-meghatározási hibát a null paramétereknél.
 */
final class DeadlineSpecifications {

    private DeadlineSpecifications() {}

    static Specification<Deadline> matching(
            Long householdId, DeadlineStatus status, DeadlineType type, LocalDate from, LocalDate to) {
        List<Specification<Deadline>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("household").get("id"), householdId));
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (from != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from));
        }
        if (to != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to));
        }
        return Specification.allOf(specs);
    }
}
