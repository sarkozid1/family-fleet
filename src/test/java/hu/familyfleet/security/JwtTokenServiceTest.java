package hu.familyfleet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Kibocsátás és ellenőrzés ugyanazzal a konfigurációval, Spring-kontextus nélkül, rögzített órákkal. */
class JwtTokenServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-10-28T09:00:00Z");
    private static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-secret-0123456789abcdefghijkl", "family-fleet", Duration.ofHours(1));

    private final JwtConfig config = new JwtConfig();
    private final AppUser user = user(42L, Role.ADMIN);

    @Test
    void issuedTokenCarriesSubjectEmailRolesAndExpiry() {
        String token = service(ISSUED).issue(user).value();

        Jwt jwt = decoder(ISSUED.plusSeconds(60)).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("admin@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED);
        assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED.plus(Duration.ofHours(1)));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(ISSUED).issue(user).value();

        // A JwtTimestampValidator alapból 60 mp óraeltérést (clock skew) enged meg.
        assertThatThrownBy(() -> decoder(ISSUED.plus(Duration.ofHours(1)).plusSeconds(61))
                        .decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtProperties otherKey =
                new JwtProperties("another-secret-0123456789abcdefghijklmn", "family-fleet", Duration.ofHours(1));
        String forged = new JwtTokenService(config.jwtEncoder(config.jwtSigningKey(otherKey)), otherKey, fixed(ISSUED))
                .issue(user)
                .value();

        assertThatThrownBy(() -> decoder(ISSUED).decode(forged))
                .isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties(PROPERTIES.secret(), "valaki-mas", Duration.ofHours(1));
        String token = new JwtTokenService(
                        config.jwtEncoder(config.jwtSigningKey(otherIssuer)), otherIssuer, fixed(ISSUED))
                .issue(user)
                .value();

        assertThatThrownBy(() -> decoder(ISSUED).decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private JwtTokenService service(Instant now) {
        return new JwtTokenService(config.jwtEncoder(config.jwtSigningKey(PROPERTIES)), PROPERTIES, fixed(now));
    }

    private JwtDecoder decoder(Instant now) {
        return config.jwtDecoder(config.jwtSigningKey(PROPERTIES), PROPERTIES, fixed(now));
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @SuppressWarnings("deprecation")
    private static AppUser user(Long id, Role role) {
        AppUser user = new AppUser(
                "Admin@Example.com", NoOpPasswordEncoder.getInstance().encode("x"), "Admin", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
