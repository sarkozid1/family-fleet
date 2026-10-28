package hu.familyfleet.security;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hitelesítés és adatszintű jogosultság végponttól végpontig, valódi tokenekkel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    /** Token nélküli kliens (a teljes filterlánccal). */
    @Autowired
    private MockMvc anonymous;

    @Autowired
    private WebApplicationContext context;

    @Test
    void registerLoginAndUseToken() throws Exception {
        String email = "Anna." + UUID.randomUUID() + "@Example.com";

        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "nagyon-titkos-1", "displayName": "Kovács Anna" }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(not(containsString("password"))));

        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "masik-jelszo-2", "displayName": "Másik" }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict());

        MvcResult login = anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "nagyon-titkos-1" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        anonymous.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Kovács Anna"));
        anonymous.perform(post("/api/households")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna háztartása" }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String email = "bela." + UUID.randomUUID() + "@example.com";
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "helyes-jelszo-1", "displayName": "Béla" }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String wrongPassword = anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "rossz-jelszo-1" }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "nincs.ilyen@example.com", "password": "rossz-jelszo-1" }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.detail"))
                .isEqualTo("Hibás e-mail-cím vagy jelszó.");
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        anonymous.perform(get("/api/households"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Hitelesítés szükséges"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        anonymous.perform(get("/api/households").header(HttpHeaders.AUTHORIZATION, "Bearer nem.ervenyes.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "rovid@example.com", "password": "rovid", "displayName": "R" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void usersSeeOnlyTheirOwnHouseholdsAndForeignOnesLookNonExistent() throws Exception {
        MockMvc owner = TestAuth.mockMvcAsNewUser(context);
        MockMvc stranger = TestAuth.mockMvcAsNewUser(context);

        long householdId = idOf(owner.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Titkos család" }
                                """))
                .andExpect(status().isCreated()).andReturn());
        long memberId = idOf(owner.perform(post("/api/households/{id}/members", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna" }
                                """))
                .andReturn());
        long vehicleId = idOf(owner.perform(post("/api/households/{id}/vehicles", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "CAR", "brand": "Skoda", "model": "Octavia", "licensePlate": "SEC001" }
                                """))
                .andReturn());
        long deadlineId = idOf(owner.perform(post("/api/households/{id}/deadlines", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OIL_CHANGE", "dueDate": "2027-01-01", "vehicleId": %d }
                                """.formatted(vehicleId)))
                .andReturn());

        // A tulajdonos látja
        owner.perform(get("/api/households"))
                .andExpect(jsonPath("$[*].id", hasItem((int) householdId)));
        owner.perform(get("/api/vehicles/{id}", vehicleId)).andExpect(status().isOk());

        // Az idegen nem látja a listában, és minden közvetlen elérés 404 (nem 403)
        stranger.perform(get("/api/households")).andExpect(jsonPath("$[*].id", not(hasItem((int) householdId))));
        stranger.perform(get("/api/households/{id}", householdId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/households/{id}/members", householdId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/households/{id}/vehicles", householdId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/households/{id}/deadlines/upcoming", householdId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/vehicles/{id}", vehicleId)).andExpect(status().isNotFound());
        stranger.perform(delete("/api/vehicles/{id}", vehicleId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/vehicles/{id}/policies", vehicleId)).andExpect(status().isNotFound());
        stranger.perform(get("/api/deadlines/{id}", deadlineId)).andExpect(status().isNotFound());
        stranger.perform(post("/api/deadlines/{id}/dismiss", deadlineId)).andExpect(status().isNotFound());
        stranger.perform(put("/api/households/{id}", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Ellopott név" }
                                """))
                .andExpect(status().isNotFound());

        // Az idegen a saját háztartásában sem hivatkozhat a másik háztartás elemeire
        long strangersHousehold = idOf(stranger.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Idegen család" }
                                """))
                .andReturn());
        stranger.perform(post("/api/households/{id}/deadlines", strangersHousehold)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "DRIVING_LICENCE", "dueDate": "2030-01-01", "memberId": %d }
                                """.formatted(memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nem létező családtag (id: %d).".formatted(memberId)));

        // A tulajdonos adatai érintetlenek
        owner.perform(get("/api/households/{id}", householdId))
                .andExpect(jsonPath("$.name").value("Titkos család"));
        owner.perform(get("/api/deadlines/{id}", deadlineId))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
