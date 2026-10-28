package hu.familyfleet.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.security.jwt.*}. A titok legalább 32 karakter (HS256-hoz legalább 256 bites kulcs
 * kell); élesben a {@code JWT_SECRET} környezeti változóból jön, a repóban soha nincs éles érték.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration ttl) {}
