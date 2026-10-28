package hu.familyfleet.deadline;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Végponttól végpontig, rögzített "mai nappal" (2026-10-28): a generált dátumok így pontosan
 * ellenőrizhetők. A {@code @Primary} óra felülírja az alkalmazás rendszerórás {@code Clock} beanjét.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeadlineApiIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Budapest"));
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void authenticate() {
        mockMvc = TestAuth.mockMvcAsNewUser(context);
    }

    @Test
    void vehicleAndKgfbPolicyGenerateDeadlinesThatRecurOnCompletion() throws Exception {
        long householdId = create("/api/households", """
                { "name": "Határidős család" }
                """);
        long vehicleId = create("/api/households/" + householdId + "/vehicles", """
                {
                  "type": "CAR", "brand": "Skoda", "model": "Octavia",
                  "licensePlate": "HAT-001", "generateTaxDeadline": true
                }
                """);
        long insurerId = ((Number) JsonPath.read(mockMvc.perform(get("/api/insurers"))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        create("/api/vehicles/" + vehicleId + "/policies", """
                {
                  "insurerId": %d, "policyType": "KGFB", "startDate": "2026-03-01",
                  "annualPremium": 52000, "paymentFrequency": "QUARTERLY"
                }
                """.formatted(insurerId));

        // Generált határidők: adó (jármű felvételekor), díjfizetés és KGFB-évforduló (kötvény létrehozásakor).
        mockMvc.perform(get("/api/households/{id}/deadlines", householdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type",
                        contains("INSURANCE_PAYMENT", "KGFB_ANNIVERSARY", "VEHICLE_TAX")))
                .andExpect(jsonPath("$.content[*].dueDate", contains("2026-12-01", "2027-03-01", "2027-03-15")))
                .andExpect(jsonPath("$.content[1].title").value("KGFB-évforduló – felmondási határidő: 2027-01-30"))
                .andExpect(jsonPath("$.content[1].vehicleId").value(vehicleId));

        mockMvc.perform(get("/api/households/{id}/deadlines", householdId).param("type", "KGFB_ANNIVERSARY"))
                .andExpect(jsonPath("$.totalElements").value(1));

        long paymentId = ((Number) JsonPath.read(mockMvc.perform(get("/api/households/{id}/deadlines", householdId)
                        .param("type", "INSURANCE_PAYMENT"))
                .andReturn().getResponse().getContentAsString(), "$.content[0].id")).longValue();

        mockMvc.perform(post("/api/deadlines/{id}/complete", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed.status").value("DONE"))
                .andExpect(jsonPath("$.completed.completedAt").value("2026-10-28T09:00:00Z"))
                .andExpect(jsonPath("$.next.dueDate").value("2027-03-01"))
                .andExpect(jsonPath("$.next.daysUntilDue").value(124));

        mockMvc.perform(post("/api/deadlines/{id}/complete", paymentId)).andExpect(status().isConflict());
    }

    @Test
    void upcomingIncludesOverdueAndRespectsWindow() throws Exception {
        long householdId = create("/api/households", """
                { "name": "Közelgő család" }
                """);
        long vehicleId = create("/api/households/" + householdId + "/vehicles", """
                { "type": "CAR", "brand": "Opel", "model": "Astra", "licensePlate": "KOZ-001" }
                """);
        createDeadline(householdId, "OIL_CHANGE", "2026-10-01", vehicleId);        // lejárt
        createDeadline(householdId, "TECHNICAL_INSPECTION", "2026-11-04", vehicleId); // 7 nap múlva
        createDeadline(householdId, "TIRE_CHANGE", "2026-11-30", vehicleId);        // 33 nap múlva

        mockMvc.perform(get("/api/households/{id}/deadlines/upcoming", householdId).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("OIL_CHANGE", "TECHNICAL_INSPECTION")))
                .andExpect(jsonPath("$[0].daysUntilDue").value(-27));

        mockMvc.perform(get("/api/households/{id}/deadlines/upcoming", householdId))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/households/{id}/deadlines/upcoming", householdId).param("days", "40"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void dismissAndValidationRules() throws Exception {
        long householdId = create("/api/households", """
                { "name": "Szabályos család" }
                """);
        long otherHouseholdId = create("/api/households", """
                { "name": "Idegen család" }
                """);
        long foreignVehicleId = create("/api/households/" + otherHouseholdId + "/vehicles", """
                { "type": "CAR", "brand": "Ford", "model": "Focus", "licensePlate": "IDG-001" }
                """);
        long deadlineId = create("/api/households/" + householdId + "/deadlines", """
                { "type": "OTHER", "dueDate": "2026-12-24", "title": "Karácsonyi ajándék" }
                """);

        mockMvc.perform(post("/api/deadlines/{id}/dismiss", deadlineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(post("/api/deadlines/{id}/dismiss", deadlineId)).andExpect(status().isConflict());

        // Kötelező kapcsolat hiányzik
        mockMvc.perform(post("/api/households/{id}/deadlines", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "TECHNICAL_INSPECTION", "dueDate": "2027-01-01" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("vehicleId")));

        // Más háztartás járműve
        mockMvc.perform(post("/api/households/{id}/deadlines", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OIL_CHANGE", "dueDate": "2027-01-01", "vehicleId": %d }
                                """.formatted(foreignVehicleId)))
                .andExpect(status().isBadRequest());

        // Nem számítható típusnál a dátum kötelező
        mockMvc.perform(post("/api/households/{id}/deadlines", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OTHER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("dueDate")));

        mockMvc.perform(get("/api/households/{id}/deadlines", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private void createDeadline(long householdId, String type, String dueDate, long vehicleId) throws Exception {
        create("/api/households/" + householdId + "/deadlines", """
                { "type": "%s", "dueDate": "%s", "vehicleId": %d }
                """.formatted(type, dueDate, vehicleId));
    }

    private long create(String url, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
