package hu.familyfleet.support;

import hu.familyfleet.security.SecurityConfig;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * {@code @WebMvcTest}-hez: a slice nem tölti be a saját {@code @Configuration} osztályainkat, ezért
 * a valódi biztonsági szabályokat ({@link SecurityConfig}) importáljuk. A tokenek dekódolása nem kell,
 * mert a tesztek {@code @WithMockUser}-rel adják a bejelentkezett felhasználót.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfig.class)
public class WebSecurityTestConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }
}
