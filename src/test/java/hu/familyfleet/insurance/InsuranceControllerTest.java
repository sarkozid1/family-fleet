package hu.familyfleet.insurance;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.insurance.dto.InsurerResponse;
import hu.familyfleet.insurance.dto.PolicyResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import hu.familyfleet.support.WebSecurityTestConfig;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(WebSecurityTestConfig.class)
@WithMockUser
@WebMvcTest({InsurerController.class, PolicyController.class})
class InsuranceControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsurerService insurerService;

    @MockitoBean
    private PolicyService policyService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInsurerReturns201() throws Exception {
        given(insurerService.create(any())).willReturn(new InsurerResponse(11L, "Új Biztosító", null, null));

        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Új Biztosító" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/insurers/11")));
    }

    @Test
    void regularUserCannotCreateInsurer() throws Exception {
        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Új Biztosító" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nincs jogosultság"));

        verifyNoInteractions(insurerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void insurerWebsiteMustBeHttpUrl() throws Exception {
        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Új Biztosító", "website": "ftp://pelda.hu" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("website"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateInsurerReturns409() throws Exception {
        given(insurerService.create(any())).willThrow(new ConflictException("Ilyen nevű biztosító már létezik: UNIQA"));

        mockMvc.perform(post("/api/insurers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "UNIQA" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void createPolicyReturns201WithPolicyLocation() throws Exception {
        given(policyService.create(eq(3L), any())).willReturn(new PolicyResponse(8L, 3L, 1L, "Allianz Hungária",
                PolicyType.KGFB, "KGFB-1", LocalDate.of(2026, 3, 1), null, new BigDecimal("52000.00"),
                PaymentFrequency.QUARTERLY, true, 0L, NOW, NOW));

        mockMvc.perform(post("/api/vehicles/3/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": 1, "policyType": "KGFB", "policyNumber": "KGFB-1",
                                  "startDate": "2026-03-01", "annualPremium": 52000,
                                  "paymentFrequency": "QUARTERLY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/policies/8")))
                .andExpect(jsonPath("$.insurerName").value("Allianz Hungária"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void endDateBeforeStartDateReturns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/vehicles/3/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": 1, "policyType": "CASCO",
                                  "startDate": "2026-03-01", "endDate": "2026-02-01",
                                  "annualPremium": 120000, "paymentFrequency": "ANNUAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("endDateAfterStartDate")));
    }

    @Test
    void premiumMustBeNonNegativeWithTwoDecimals() throws Exception {
        mockMvc.perform(post("/api/vehicles/3/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": 1, "policyType": "KGFB", "startDate": "2026-03-01",
                                  "annualPremium": -1.555, "paymentFrequency": "ANNUAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("annualPremium")));
    }

    @Test
    void unknownEnumValueReturns400() throws Exception {
        mockMvc.perform(post("/api/vehicles/3/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": 1, "policyType": "LIFE", "startDate": "2026-03-01",
                                  "annualPremium": 1000, "paymentFrequency": "ANNUAL"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void namedDatabaseConstraintIsTranslatedToReadableMessage() throws Exception {
        given(policyService.update(eq(8L), any())).willThrow(new DataIntegrityViolationException("could not execute",
                new ConstraintViolationException("ERROR: conflicting key value violates exclusion constraint",
                        new SQLException("raw sql message", "23P01"), "ex_policy_kgfb_no_overlap")));

        mockMvc.perform(put("/api/policies/8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "insurerId": 1, "startDate": "2026-03-01", "annualPremium": 52000,
                                  "paymentFrequency": "ANNUAL", "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A járműnek erre az időszakra már van érvényes KGFB-kötvénye."));
    }
}
