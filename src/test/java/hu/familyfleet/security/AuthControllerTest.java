package hu.familyfleet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hu.familyfleet.security.dto.LoginRequest;
import hu.familyfleet.security.dto.TokenResponse;
import hu.familyfleet.security.dto.UserResponse;
import hu.familyfleet.support.WebSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerIsPublicAndReturns201() throws Exception {
        given(authService.register(any()))
                .willReturn(new UserResponse(
                        1L, "anna@example.com", "Anna", Role.USER, Instant.parse("2026-10-28T10:00:00Z")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "anna@example.com", "password": "nagyon-titkos-1", "displayName": "Anna" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/auth/me")));
    }

    @Test
    void registerValidatesEmailAndPasswordLength() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "nem-email", "password": "%s", "displayName": "Anna" }
                                """.formatted("x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));

        verifyNoInteractions(authService);
    }

    @Test
    void loginIsPublicAndReturnsBearerToken() throws Exception {
        given(authService.login(any())).willReturn(TokenResponse.bearer("eyJ.token.value", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "anna@example.com", "password": "nagyon-titkos-1" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("eyJ.token.value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void badCredentialsReturn401ProblemDetail() throws Exception {
        given(authService.login(any())).willThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "anna@example.com", "password": "rossz" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Hibás e-mail-cím vagy jelszó."));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void meReturnsCurrentUserWhenAuthenticated() throws Exception {
        given(authService.me())
                .willReturn(new UserResponse(
                        1L, "anna@example.com", "Anna", Role.USER, Instant.parse("2026-10-28T10:00:00Z")));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@example.com"));
    }

    @Test
    void passwordIsNeverPartOfToString() {
        assertThat(new LoginRequest("anna@example.com", "nagyon-titkos-1").toString())
                .doesNotContain("titkos");
    }
}
