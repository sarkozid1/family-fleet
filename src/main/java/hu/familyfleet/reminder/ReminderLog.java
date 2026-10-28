package hu.familyfleet.reminder;

import hu.familyfleet.household.Household;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Egy háztartásnak adott napon elküldött emlékeztető. Csak beszúrjuk, sosem módosítjuk. */
@Entity
@Table(name = "reminder_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(nullable = false)
    private LocalDate reminderDate;

    @Column(nullable = false)
    private int recipientCount;

    @Column(nullable = false)
    private int deadlineCount;

    @Column(nullable = false)
    private Instant sentAt;

    public ReminderLog(
            Household household, LocalDate reminderDate, int recipientCount, int deadlineCount, Instant sentAt) {
        this.household = Objects.requireNonNull(household, "household");
        this.reminderDate = Objects.requireNonNull(reminderDate, "reminderDate");
        this.recipientCount = recipientCount;
        this.deadlineCount = deadlineCount;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }
}
