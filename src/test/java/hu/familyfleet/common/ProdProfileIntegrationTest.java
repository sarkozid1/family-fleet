package hu.familyfleet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.familyfleet.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A prod profil konfigurációja: a kötelező környezeti változókat itt property-ként adjuk meg
 * (a tényleges adatbázis-kapcsolatot a Testcontainers {@code @ServiceConnection} adja).
 */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://overridden-by-testcontainers/familyfleet",
            "DB_USERNAME=unused",
            "DB_PASSWORD=unused",
            "MAIL_HOST=smtp.example.com",
            "REMINDER_FROM=noreply@example.com",
            "JWT_SECRET=prod-test-secret-0123456789abcdefghijklmnop"
        })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void exposesOnlyHealthAndInfoAndHidesApiDocs() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        // Névtelenül a nem nyilvános útvonal 401 (azt sem áruljuk el, létezik-e), bejelentkezve 404.
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(jwt())).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/loggers").with(jwt())).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void secretsAndHostsComeFromEnvironmentAndSqlIsNotLogged() {
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("smtp.example.com");
        assertThat(environment.getProperty("spring.mail.port")).isEqualTo("587");
        assertThat(environment.getProperty("app.reminder.from")).isEqualTo("noreply@example.com");
        assertThat(environment.getProperty("logging.level.org.hibernate.SQL")).isNull();
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }
}
