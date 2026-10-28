package hu.familyfleet.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import hu.familyfleet.common.logging.RequestIdFilter;
import hu.familyfleet.security.AppUser;
import hu.familyfleet.security.AppUserRepository;
import hu.familyfleet.security.JwtTokenService;
import hu.familyfleet.security.Role;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integrációs tesztekhez: létrehoz egy friss felhasználót, valódi JWT-t bocsát ki neki, és olyan
 * MockMvc-t ad vissza, amely minden kérésben elküldi a tokent. Így a tesztek a teljes biztonsági
 * láncon (token-ellenőrzés + háztartásszintű jogosultság) mennek át.
 */
public final class TestAuth {

    private TestAuth() {}

    public static MockMvc mockMvcAsNewUser(WebApplicationContext context) {
        return mockMvcAs(context, Role.USER);
    }

    public static MockMvc mockMvcAs(WebApplicationContext context, Role role) {
        AppUserRepository users = context.getBean(AppUserRepository.class);
        PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
        AppUser user = users.save(new AppUser(
                "user-" + UUID.randomUUID() + "@test.local",
                passwordEncoder.encode("titkos-jelszo-123"),
                "Teszt Elek",
                role));
        String token = context.getBean(JwtTokenService.class).issue(user).value();

        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .addFilters(context.getBean(RequestIdFilter.class))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
    }
}
