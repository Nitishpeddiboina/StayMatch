package com.staymatch.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.staymatch.config.SecurityConfig;
import com.staymatch.dto.OwnerPropertyRequest;
import com.staymatch.dto.OwnerPropertyResponse;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.DatabaseUserDetailsService;
import com.staymatch.service.OwnerPropertyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerPropertyController.class)
@Import({SecurityConfig.class, OwnerPropertyExceptionHandler.class})
class OwnerPropertyControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OwnerPropertyService ownerPropertyService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void ownerCanCreatePropertyAndClientOwnerIdIsIgnored() throws Exception {
        StayMatchUserDetails principal = principal(42L, Role.OWNER);
        when(ownerPropertyService.create(eq(principal), any(OwnerPropertyRequest.class)))
                .thenReturn(response(12L, "PG"));

        mockMvc.perform(post("/api/v1/owner/properties")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Shared PG","type":"PG","bhk":null,"sharingType":2,
                                 "area":"Gachibowli","city":"Hyderabad","addressLine":"Fictional address",
                                 "monthlyRent":9500,"deposit":0,"genderPreference":"ANY","ownerId":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.ownerId").doesNotExist());

        verify(ownerPropertyService).create(eq(principal), any(OwnerPropertyRequest.class));
    }

    @Test
    void ownerCanListOnlyViaOwnerService() throws Exception {
        StayMatchUserDetails principal = principal(42L, Role.OWNER);
        when(ownerPropertyService.listMine(principal)).thenReturn(List.of(response(12L, "PG")));

        mockMvc.perform(get("/api/v1/owner/properties").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());

        verify(ownerPropertyService).listMine(principal);
    }

    @Test
    void seekerCannotAccessOwnerManagement() throws Exception {
        mockMvc.perform(get("/api/v1/owner/properties").with(user(principal(43L, Role.SEEKER))))
                .andExpect(status().isForbidden());

        verify(ownerPropertyService, never()).listMine(any());
    }

    @Test
    void anonymousOwnerRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/owner/properties")).andExpect(status().isUnauthorized());
        verify(ownerPropertyService, never()).listMine(any());
    }

    @Test
    void ownerWriteRequestStillRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/owner/properties")
                        .with(user(principal(42L, Role.OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verify(ownerPropertyService, never()).create(any(), any());
    }

    @Test
    void invalidPropertyFieldsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/owner/properties")
                        .with(user(principal(42L, Role.OWNER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","type":"PG","sharingType":2,"area":"Gachibowli",
                                 "city":"Hyderabad","addressLine":"Fictional address","monthlyRent":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Property request validation failed."));
        verify(ownerPropertyService, never()).create(any(), any());
    }

    @Test
    void missingRentReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/owner/properties")
                        .with(user(principal(42L, Role.OWNER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Shared PG","type":"PG","sharingType":2,
                                 "area":"Gachibowli","city":"Hyderabad","addressLine":"Fictional address"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Property request validation failed."));
        verify(ownerPropertyService, never()).create(any(), any());
    }

    private StayMatchUserDetails principal(Long id, Role role) {
        User user = new User("Test User", "test@example.test", "unused-hash", role);
        user.setId(id);
        return new StayMatchUserDetails(user);
    }

    private OwnerPropertyResponse response(Long id, String type) {
        return new OwnerPropertyResponse(id, "Listing", type, null, 2,
                "Gachibowli", "Hyderabad", "Fictional address", null, null, null,
                9500, 0, "ANY", true, null, List.of(), null, null);
    }
}
