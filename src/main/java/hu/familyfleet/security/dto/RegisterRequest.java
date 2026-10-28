package hu.familyfleet.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Regisztráció. A jelszó felső korlátja 72: a BCrypt csak az első 72 bájtot veszi figyelembe,
 * a hosszabb jelszó vége csendben elveszne.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=%s, displayName=%s]".formatted(email, displayName);
    }
}
