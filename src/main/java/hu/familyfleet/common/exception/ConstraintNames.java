package hu.familyfleet.common.exception;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;

/** A megsértett adatbázis-megkötés nevének kinyerése egy kivételláncból. */
public final class ConstraintNames {

    /** PostgreSQL-üzenet, pl.: {@code violates exclusion constraint "ex_policy_kgfb_no_overlap"}. */
    private static final Pattern POSTGRES_CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");

    private ConstraintNames() {}

    /**
     * Először a Hibernate által kinyert nevet használja. A PostgreSQL dialektus ezt az EXCLUDE
     * megkötésnél (SQLState 23P01) nem tölti ki, ezért ilyenkor az adatbázis üzenetéből olvassuk ki.
     */
    public static Optional<String> extract(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName().toLowerCase(Locale.ROOT));
            }
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                Matcher matcher = POSTGRES_CONSTRAINT.matcher(cause.getMessage());
                if (matcher.find()) {
                    return Optional.of(matcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return Optional.empty();
    }
}
