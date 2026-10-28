package hu.familyfleet.household;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.household.dto.HouseholdRequest;
import hu.familyfleet.household.dto.HouseholdResponse;
import hu.familyfleet.household.dto.MemberResponse;
import hu.familyfleet.support.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Csak a webréteg: routing, JSON, validáció, hibaformátum. A service mock. */
@Import(WebSecurityTestConfig.class)
@WithMockUser
@WebMvcTest(HouseholdController.class)
class HouseholdControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        given(householdService.create(new HouseholdRequest("Kovács család")))
                .willReturn(new HouseholdResponse(42L, "Kovács család", CREATED_AT));

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Kovács család" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/households/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Kovács család"));
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/households"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(householdService);
    }

    @Test
    void createWithBlankNameReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "  " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        verifyNoInteractions(householdService);
    }

    @Test
    void createWithTooLongNameReturns400() throws Exception {
        String tooLong = "x".repeat(101);

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("name")));
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/households/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownHouseholdReturns404ProblemDetail() throws Exception {
        given(householdService.findById(99L)).willThrow(new ResourceNotFoundException("Háztartás", 99L));

        mockMvc.perform(get("/api/households/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nem található"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    void membersOfUnknownHouseholdReturn404() throws Exception {
        given(householdService.findMembers(99L)).willThrow(new ResourceNotFoundException("Háztartás", 99L));

        mockMvc.perform(get("/api/households/99/members")).andExpect(status().isNotFound());
    }

    @Test
    void addMemberWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/households/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna", "email": "nem-email" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void addMemberReturns201WithMemberLocation() throws Exception {
        given(householdService.addMember(eq(1L), any()))
                .willReturn(new MemberResponse(7L, 1L, "Anna", "anna@example.com", CREATED_AT));

        mockMvc.perform(post("/api/households/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Anna", "email": "anna@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/households/1/members/7")))
                .andExpect(jsonPath("$.householdId").value(1));
    }

    @Test
    void listsHouseholds() throws Exception {
        given(householdService.findAll())
                .willReturn(List.of(
                        new HouseholdResponse(1L, "A család", CREATED_AT),
                        new HouseholdResponse(2L, "B család", CREATED_AT)));

        mockMvc.perform(get("/api/households"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].name").value("B család"));
    }

    @Test
    void constraintViolationReturns409WithoutSqlDetails() throws Exception {
        given(householdService.update(eq(1L), any()))
                .willThrow(new DataIntegrityViolationException(
                        "could not execute statement [ERROR: duplicate key value violates unique constraint \"x\"]"));

        mockMvc.perform(put("/api/households/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Kovács család" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", not(containsString("constraint"))))
                .andExpect(content().string(not(containsString("duplicate key"))));
    }
}
