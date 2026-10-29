package hu.familyfleet.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.registration.*}: ki regisztrálhat.
 *
 * @param open          ha {@code true}, bárki (fejlesztés, tesztek); élesben alapból {@code false}
 * @param allowedEmails zárt regisztrációnál az engedélyezett címek (a meghívottakon felül)
 */
@ConfigurationProperties(prefix = "app.security.registration")
public record RegistrationProperties(boolean open, List<String> allowedEmails) {

    public RegistrationProperties {
        allowedEmails = allowedEmails == null
                ? List.of()
                : allowedEmails.stream()
                        .filter(email -> email != null && !email.isBlank())
                        .map(AppUser::normalizeEmail)
                        .toList();
    }
}
