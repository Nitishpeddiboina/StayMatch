package com.staymatch.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.staymatch.config.SecurityConfig;
import com.staymatch.dto.PropertyPhotoResponse;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.DatabaseUserDetailsService;
import com.staymatch.service.PropertyPhotoService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerPropertyPhotoController.class)
@Import({SecurityConfig.class, OwnerPropertyExceptionHandler.class})
class OwnerPropertyPhotoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyPhotoService photoService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void ownerCanListAndUploadPhotos() throws Exception {
        StayMatchUserDetails owner = principal(42L, Role.OWNER);
        PropertyPhotoResponse photo = new PropertyPhotoResponse(9L,
                "/api/v1/properties/8/photos/9", "image/png", 120L, LocalDateTime.now());
        when(photoService.listMine(owner, 8L)).thenReturn(List.of(photo));
        when(photoService.upload(eq(owner), eq(8L), any())).thenReturn(List.of(photo));

        mockMvc.perform(get("/api/v1/owner/properties/8/photos").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("/api/v1/properties/8/photos/9"))
                .andExpect(jsonPath("$[0].storagePath").doesNotExist());

        MockMultipartFile file = new MockMultipartFile("photos", "photo.png", "image/png", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/owner/properties/8/photos").file(file)
                        .with(user(owner)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].contentType").value("image/png"));

        verify(photoService).listMine(owner, 8L);
        verify(photoService).upload(eq(owner), eq(8L), any());
    }

    @Test
    void seekerAndAnonymousUsersCannotListOwnerPhotos() throws Exception {
        mockMvc.perform(get("/api/v1/owner/properties/8/photos").with(user(principal(43L, Role.SEEKER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/owner/properties/8/photos"))
                .andExpect(status().isUnauthorized());
        verify(photoService, never()).listMine(any(), any());
    }

    @Test
    void ownerPhotoUploadRequiresCsrf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("photos", "photo.png", "image/png", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/owner/properties/8/photos").file(file)
                        .with(user(principal(42L, Role.OWNER))))
                .andExpect(status().isForbidden());
        verify(photoService, never()).upload(any(), any(), any());
    }

    @Test
    void ownerPhotoDeletionRequiresCsrfAndSeekerRoleCannotDelete() throws Exception {
        StayMatchUserDetails owner = principal(42L, Role.OWNER);
        mockMvc.perform(delete("/api/v1/owner/properties/8/photos/9").with(user(owner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/owner/properties/8/photos/9")
                        .with(user(principal(43L, Role.SEEKER))).with(csrf()))
                .andExpect(status().isForbidden());
        verify(photoService, never()).delete(any(), any(), any());

        mockMvc.perform(delete("/api/v1/owner/properties/8/photos/9")
                        .with(user(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        verify(photoService).delete(owner, 8L, 9L);
    }

    private StayMatchUserDetails principal(Long id, Role role) {
        User user = new User("Test User", "test@example.test", "unused-hash", role);
        user.setId(id);
        return new StayMatchUserDetails(user);
    }
}
