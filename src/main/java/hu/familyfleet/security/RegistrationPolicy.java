package hu.familyfleet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ki regisztrálhat: nyitott módban bárki; zárt módban (élesben) csak az engedélyezett címek és
 * azok, akiket valaki meghívott egy háztartásba.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationPolicy {

    private final RegistrationProperties properties;
    private final InvitationLookup invitationLookup;

    public boolean allows(String normalizedEmail) {
        return properties.open()
                || properties.allowedEmails().contains(normalizedEmail)
                || invitationLookup.hasPendingInvitation(normalizedEmail);
    }
}
