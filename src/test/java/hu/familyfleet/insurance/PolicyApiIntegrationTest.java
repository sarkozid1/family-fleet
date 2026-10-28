package hu.familyfleet.insurance;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PolicyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seededInsurersAreListedAndNewOneCanBeAdded() throws Exception {
        mockMvc.perform(get("/api/insurers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Allianz Hungária")));

        String name = "Teszt Biztosító " + UUID.randomUUID();
        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "phone": "+36 1 234 5678", "website": "https://pelda.hu" }
                                """.formatted(name)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyOneActiveKgfbPerVehicle() throws Exception {
        long vehicleId = createVehicle();
        long insurerId = firstInsurerId();

        String firstUrl = mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "KGFB", "2026-01-01", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getHeader("Location");

        // Második, átfedő KGFB: a domain-szabály 409-cel elutasítja.
        mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "KGFB", "2026-09-01", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("KGFB")));

        // CASCO mellette nyugodtan lehet.
        mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "CASCO", "2026-01-01", null)))
                .andExpect(status().isCreated());

        // Az első KGFB lezárása után az új, aznap induló KGFB már nem ütközik.
        mockMvc.perform(put(firstUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": %d, "startDate": "2026-01-01", "endDate": "2026-09-01",
                                  "annualPremium": 52000, "paymentFrequency": "QUARTERLY", "version": 0
                                }
                                """.formatted(insurerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2026-09-01"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "KGFB", "2026-09-01", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/vehicles/{id}/policies", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startDate").value("2026-09-01"));
    }

    @Test
    void invalidDatesAndReferencesAreClientErrors() throws Exception {
        long vehicleId = createVehicle();
        long insurerId = firstInsurerId();

        mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "CASCO", "2026-05-01", "2026-05-01")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/vehicles/{id}/policies", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(Long.MAX_VALUE, "CASCO", "2026-05-01", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("biztosító")));

        mockMvc.perform(post("/api/vehicles/{id}/policies", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(insurerId, "CASCO", "2026-05-01", null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/policies/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private long createVehicle() throws Exception {
        MvcResult household = mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Biztosított család" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long householdId = idOf(household);
        return idOf(mockMvc.perform(post("/api/households/{id}/vehicles", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "CAR", "brand": "Skoda", "model": "Octavia", "licensePlate": "KGF001" }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long firstInsurerId() throws Exception {
        Number id = JsonPath.read(
                mockMvc.perform(get("/api/insurers")).andReturn().getResponse().getContentAsString(), "$[0].id");
        return id.longValue();
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String policyJson(long insurerId, String type, String start, String end) {
        return """
                {
                  "insurerId": %d, "policyType": "%s", "startDate": "%s", "endDate": %s,
                  "annualPremium": 52000, "paymentFrequency": "QUARTERLY"
                }
                """.formatted(insurerId, type, start, end == null ? "null" : "\"" + end + "\"");
    }
}
