package hu.familyfleet.household;

import hu.familyfleet.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Végponttól végpontig: HTTP -> controller -> service -> JPA -> valódi PostgreSQL (Testcontainers).
 * Nincs {@code @Transactional} a teszten, így a service saját tranzakciói ugyanúgy futnak, mint élesben;
 * az ütközések elkerülésére minden teszt saját háztartást hoz létre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HouseholdApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void householdAndMemberLifecycle() throws Exception {
        String householdUrl = mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "  Szabó család " }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Szabó család"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(put(householdUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Szabó-Kiss család" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Szabó-Kiss család"));

        String memberUrl = mockMvc.perform(post(householdUrl + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Péter", "email": "Peter@Example.com" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(post(householdUrl + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get(memberUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("peter@example.com"));

        mockMvc.perform(get(householdUrl + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Anna", "Péter")));
    }

    @Test
    void membersOfUnknownHouseholdReturn404NotEmptyList() throws Exception {
        mockMvc.perform(get("/api/households/{id}/members", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/households/" + Long.MAX_VALUE + "/members"));
    }

    @Test
    void addingMemberToUnknownHouseholdReturns404() throws Exception {
        mockMvc.perform(post("/api/households/{id}/members", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna" }
                                """))
                .andExpect(status().isNotFound());
    }
}
