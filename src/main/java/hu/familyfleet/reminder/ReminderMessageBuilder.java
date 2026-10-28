package hu.familyfleet.reminder;

import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Az emlékeztető-levél szövege. Tiszta függvény: a "mai napot" a hívó adja át. */
final class ReminderMessageBuilder {

    private static final Map<DeadlineType, String> TYPE_NAMES = Map.of(
            DeadlineType.TECHNICAL_INSPECTION, "Műszaki vizsga",
            DeadlineType.INSURANCE_PAYMENT, "Biztosítási díj",
            DeadlineType.KGFB_ANNIVERSARY, "KGFB-évforduló",
            DeadlineType.VEHICLE_TAX, "Gépjárműadó",
            DeadlineType.MOTORWAY_VIGNETTE, "Autópálya-matrica",
            DeadlineType.OIL_CHANGE, "Olajcsere",
            DeadlineType.TIRE_CHANGE, "Gumicsere",
            DeadlineType.DRIVING_LICENCE, "Jogosítvány",
            DeadlineType.OTHER, "Egyéb");

    private ReminderMessageBuilder() {}

    static String subject(LocalDate today) {
        return "Family Fleet – közelgő határidők (%s)".formatted(today);
    }

    /** A határidők a hátralévő napok szerint csoportosítva, a legsürgősebb elöl. */
    static String body(String householdName, LocalDate today, List<Deadline> deadlines) {
        Map<Long, List<Deadline>> byDaysLeft = deadlines.stream()
                .collect(Collectors.groupingBy(
                        deadline -> ChronoUnit.DAYS.between(today, deadline.getDueDate()),
                        TreeMap::new,
                        Collectors.toList()));

        StringBuilder text = new StringBuilder()
                .append("Kedves ")
                .append(householdName)
                .append("!\n\n")
                .append("A következő határidők közelednek:\n");
        byDaysLeft.forEach((daysLeft, group) -> {
            text.append('\n').append(daysLeftLabel(daysLeft)).append(":\n");
            group.forEach(deadline -> text.append("  - ")
                    .append(deadline.getDueDate())
                    .append(" – ")
                    .append(describe(deadline))
                    .append('\n'));
        });
        return text.append("\nEzt a levelet a Family Fleet küldte automatikusan.\n")
                .toString();
    }

    static String describe(Deadline deadline) {
        String typeName = TYPE_NAMES.get(deadline.getType());
        return deadline.getTitle() == null ? typeName : typeName + ": " + deadline.getTitle();
    }

    private static String daysLeftLabel(long daysLeft) {
        return daysLeft == 1 ? "Holnap" : daysLeft + " nap múlva";
    }
}
