package hu.familyfleet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.common.logging.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Üzemeltetési végpontok és a kérésazonosító a közös (profil nélküli) konfigurációval. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUpWithoutDetailsAndProbesAreAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$", not(hasKey("components"))));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void infoShowsBuildAndJavaVersion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("family-fleet"))
                .andExpect(jsonPath("$.java.version").isNotEmpty());
    }

    /** A felület statikus fájljai bejelentkezés nélkül is elérhetők (a nem létező fájl 404, nem 401). */
    @Test
    void frontendStaticFilesArePublic() throws Exception {
        mockMvc.perform(get("/assets/nincs-ilyen.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/favicon.svg")).andExpect(status().is(not(401)));
        mockMvc.perform(get("/api/households")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyHealthAndInfoAreExposedByDefault() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(jwt())).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void openApiDescribesEndpointsAndValidationLimits() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Family Fleet API"))
                .andExpect(jsonPath("$.paths['/api/households/{householdId}/vehicles'].post")
                        .exists())
                .andExpect(
                        jsonPath("$.paths['/api/deadlines/{id}/complete'].post").exists())
                .andExpect(jsonPath("$.components.schemas.HouseholdRequest.properties.name.maxLength")
                        .value(100));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesRequestIdAndProblemDetailRepeatsIt() throws Exception {
        // Token nélkül: a 401-es választ már a biztonsági lánc adja, a requestId ott is benne van.
        MvcResult result = mockMvc.perform(get("/api/households/{id}", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + requestId + "\"");
    }

    @Test
    void incomingRequestIdIsPropagated() throws Exception {
        mockMvc.perform(get("/api/households/{id}", Long.MAX_VALUE).header(RequestIdFilter.HEADER, "client-req-7"))
                .andExpect(header().string(RequestIdFilter.HEADER, "client-req-7"))
                .andExpect(jsonPath("$.requestId").value("client-req-7"));
    }
}
