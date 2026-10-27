package com.staymatch.controller;

import com.staymatch.config.SecurityConfig;
import com.staymatch.dto.RegistrationResponse;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.EmailAlreadyRegisteredException;
import com.staymatch.service.DatabaseUserDetailsService;
import com.staymatch.service.LoginService;
import com.staymatch.service.PropertyMatchingService;
import com.staymatch.service.PropertyService;
import com.staymatch.service.RegistrationService;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, PropertyController.class})
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private LoginService loginService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @MockBean
    private PropertyService propertyService;

    @MockBean
    private PropertyMatchingService propertyMatchingService;

    @Test
    void registrationReturnsCreatedAndOnlySafeUserFields() throws Exception {
        when(registrationService.register(any())).thenReturn(new RegistrationResponse(
                7L, "Alice", "alice@example.com", Role.SEEKER,
                LocalDateTime.of(2026, 1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"Alice@example.com","password":"correct horse battery staple","role":"SEEKER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("SEEKER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(registrationService).register(any());
    }

    @Test
    void invalidRegistrationFieldsReturnSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","email":"not-an-email","password":"short","role":"SEEKER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed."));
    }

    @Test
    void oversizedUtf8PasswordReturnsBadRequest() throws Exception {
        String multibytePassword = "界".repeat(25);
        String body = "{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\""
                + multibytePassword + "\",\"role\":\"SEEKER\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed."));
    }

    @Test
    void unsupportedRoleReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@example.com","password":"correct horse battery staple","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid registration request."));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(registrationService.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@example.com","password":"correct horse battery staple","role":"SEEKER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void unlistedEndpointsRemainProtected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPropertySearchIsPublic() throws Exception {
        when(propertyService.searchProperties(null, null, null, null, null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anonymousPropertyMatchesArePublic() throws Exception {
        when(propertyMatchingService.findMatches(null, null, null, null, null, null, null, List.of()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/properties/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousClientCanObtainMaterializedSessionCsrfToken() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertNotNull(result.getRequest().getSession(false));
    }

    @Test
    void tokenFetchedAnonymouslyAllowsLoginRequestToReachAuthentication() throws Exception {
        when(loginService.authenticate("Alice@Example.com", "incorrect password"))
                .thenThrow(new BadCredentialsException("internal detail"));
        var tokenResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .header(tokenResponse.path("headerName").asText(), tokenResponse.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Alice@Example.com","password":"incorrect password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        verify(loginService).authenticate("Alice@Example.com", "incorrect password");
    }

    @Test
    void invalidLoginFieldsReturnBadRequestWithoutAuthenticating() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed."));

        verify(loginService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void invalidCredentialsReturnGenericUnauthorizedResponse() throws Exception {
        when(loginService.authenticate("Alice@Example.com", "correct horse battery staple"))
                .thenThrow(new BadCredentialsException("internal detail"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void successfulLoginCreatesSessionAndReturnsOnlySafeFields() throws Exception {
        when(loginService.authenticate("Alice@Example.com", "correct horse battery staple"))
                .thenReturn(authentication());
        CsrfSession csrfSession = fetchCsrfSession();
        String previousSessionId = csrfSession.session().getId();

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrfSession.session())
                        .header(csrfSession.headerName(), csrfSession.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(23))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        org.junit.jupiter.api.Assertions.assertNotEquals(previousSessionId, session.getId());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(23))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesAuthenticatedSession() throws Exception {
        when(loginService.authenticate("Alice@Example.com", "correct horse battery staple"))
                .thenReturn(authentication());
        CsrfSession csrfSession = fetchCsrfSession();
        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrfSession.session())
                        .header(csrfSession.headerName(), csrfSession.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .header(csrfSession.headerName(), csrfSession.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("JSESSIONID=")));
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    private Authentication authentication() {
        User user = new User("Alice", "alice@example.com", "not-returned-hash", Role.OWNER);
        user.setId(23L);
        StayMatchUserDetails principal = new StayMatchUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private String loginJson() {
        return """
                {"email":" Alice@Example.com ","password":"correct horse battery staple"}
                """;
    }

    private CsrfSession fetchCsrfSession() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfSession(
                tokenResponse.path("headerName").asText(),
                tokenResponse.path("token").asText(),
                (MockHttpSession) result.getRequest().getSession(false));
    }

    private record CsrfSession(String headerName, String token, MockHttpSession session) {
    }
}
