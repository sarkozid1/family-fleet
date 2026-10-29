package hu.familyfleet.household;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.household.dto.SharingEntryResponse;
import hu.familyfleet.household.dto.SharingEntryResponse.Status;
import hu.familyfleet.support.WebSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HouseholdSharingController.class)
@Import(WebSecurityTestConfig.class)
@WithMockUser
class HouseholdSharingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdSharingService sharingService;

    @Test
    void shareReturns201WithEntry() throws Exception {
        given(sharingService.share(eq(1L), eq("anna@example.com")))
                .willReturn(new SharingEntryResponse(
                        Status.PENDING, null, 5L, "anna@example.com", null, false, Instant.now()));

        mockMvc.perform(post("/api/households/1/sharing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "anna@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.invitationId").value(5));
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/households/1/sharing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "nem-email" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));

        verifyNoInteractions(sharingService);
    }

    @Test
    void revokingLastAccessReturns409() throws Exception {
        willThrow(new ConflictException("Az utolsó hozzáférés nem vonható vissza."))
                .given(sharingService)
                .revokeUser(1L, 7L);

        mockMvc.perform(delete("/api/households/1/sharing/users/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Az utolsó hozzáférés nem vonható vissza."));
    }

    @Test
    void revokingInvitationReturns204() throws Exception {
        mockMvc.perform(delete("/api/households/1/sharing/invitations/5")).andExpect(status().isNoContent());

        verify(sharingService).revokeInvitation(1L, 5L);
    }
}
