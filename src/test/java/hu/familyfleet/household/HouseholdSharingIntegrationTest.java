package hu.familyfleet.household;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.security.Role;
import hu.familyfleet.support.TestAuth;
import hu.familyfleet.support.TestAuth.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * Megosztás és zárt regisztráció (mint élesben): csak az engedélyezett cím és a meghívottak
 * regisztrálhatnak, a meghívó regisztrációkor hozzáféréssé válik.
 */
@SpringBootTest(
        properties = {
            "app.security.registration.open=false",
            "app.security.registration.allowed-emails=Allowed.Parent@Example.com"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HouseholdSharingIntegrationTest {

    @Autowired
    private MockMvc anonymous;

    @Autowired
    private WebApplicationContext context;

    @Test
    void sharingWithExistingUserGrantsAccessImmediately() throws Exception {
        TestUser owner = TestAuth.newUser(context, Role.USER);
        TestUser partner = TestAuth.newUser(context, Role.USER);
        long householdId = createHousehold(owner.mockMvc(), "Közös család");

        partner.mockMvc().perform(get("/api/households/{id}", householdId)).andExpect(status().isNotFound());

        share(owner.mockMvc(), householdId, partner.email().toUpperCase())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value(partner.id()));

        partner.mockMvc().perform(get("/api/households/{id}", householdId)).andExpect(status().isOk());
        partner.mockMvc()
                .perform(get("/api/households/{id}/sharing", householdId))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder(owner.email(), partner.email())))
                .andExpect(jsonPath("$[?(@.you == true)].email", contains(partner.email())));

        share(owner.mockMvc(), householdId, partner.email())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("már hozzáfér")));
    }

    @Test
    void invitationAllowsRegistrationAndBecomesAccess() throws Exception {
        TestUser owner = TestAuth.newUser(context, Role.USER);
        long householdId = createHousehold(owner.mockMvc(), "Meghívós család");
        String invited = "invited-" + UUID.randomUUID() + "@example.com";

        // Meghívó nélkül zárt a regisztráció
        register(invited).andExpect(status().isForbidden()).andExpect(jsonPath("$.detail", containsString("meghívót")));

        share(owner.mockMvc(), householdId, invited)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        share(owner.mockMvc(), householdId, invited).andExpect(status().isConflict());

        register(invited).andExpect(status().isCreated());
        MockMvc newcomer = TestAuth.mockMvcWithToken(context, login(invited));

        newcomer.perform(get("/api/households")).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains((int)
                householdId)));
        owner.mockMvc()
                .perform(get("/api/households/{id}/sharing", householdId))
                .andExpect(jsonPath("$[*].status", contains("ACTIVE", "ACTIVE")));
    }

    @Test
    void explicitlyAllowedEmailCanRegisterButStrangersCannot() throws Exception {
        register("allowed.parent@example.com").andExpect(status().isCreated());
        register("stranger-" + UUID.randomUUID() + "@example.com").andExpect(status().isForbidden());
    }

    @Test
    void revokingInvitationAndAccessButNeverTheLastOne() throws Exception {
        TestUser owner = TestAuth.newUser(context, Role.USER);
        TestUser partner = TestAuth.newUser(context, Role.USER);
        TestUser stranger = TestAuth.newUser(context, Role.USER);
        long householdId = createHousehold(owner.mockMvc(), "Visszavonós család");
        share(owner.mockMvc(), householdId, partner.email()).andExpect(status().isCreated());
        Number invitationId = JsonPath.read(
                share(owner.mockMvc(), householdId, "later-" + UUID.randomUUID() + "@example.com")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.invitationId");

        stranger.mockMvc()
                .perform(get("/api/households/{id}/sharing", householdId))
                .andExpect(status().isNotFound());

        owner.mockMvc()
                .perform(delete("/api/households/{id}/sharing/invitations/{inv}", householdId, invitationId))
                .andExpect(status().isNoContent());
        owner.mockMvc()
                .perform(delete("/api/households/{id}/sharing/users/{user}", householdId, partner.id()))
                .andExpect(status().isNoContent());
        partner.mockMvc().perform(get("/api/households/{id}", householdId)).andExpect(status().isNotFound());

        owner.mockMvc()
                .perform(delete("/api/households/{id}/sharing/users/{user}", householdId, owner.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("utolsó hozzáférés")));
    }

    private long createHousehold(MockMvc mockMvc, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions share(MockMvc mockMvc, long householdId, String email) throws Exception {
        return mockMvc.perform(post("/api/households/{id}/sharing", householdId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s" }
                        """.formatted(email)));
    }

    private ResultActions register(String email) throws Exception {
        return anonymous.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "csaladi-jelszo-1", "displayName": "Új tag" }
                        """.formatted(email)));
    }

    private String login(String email) throws Exception {
        String body = anonymous
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "csaladi-jelszo-1" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
