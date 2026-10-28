package hu.familyfleet.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** A kérést küldő felhasználó azonosítója a JWT {@code sub} claimjéből. */
@Component
public class CurrentUser {

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Long.valueOf(jwt.getToken().getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Nincs bejelentkezett felhasználó.");
    }
}
