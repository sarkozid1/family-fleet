package hu.familyfleet.security.dto;

import hu.familyfleet.security.AppUser;
import hu.familyfleet.security.Role;
import java.time.Instant;

/** A felhasználó nyilvános adatai; a jelszó-hash soha nem kerül ki. */
public record UserResponse(Long id, String email, String displayName, Role role, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getCreatedAt());
    }
}
