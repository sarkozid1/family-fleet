package hu.familyfleet.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Közös mezők a gyakran módosuló entitásokhoz: optimista zárolás + auditidőbélyegek.
 */
@MappedSuperclass
@Getter
public abstract class AuditableEntity {

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
