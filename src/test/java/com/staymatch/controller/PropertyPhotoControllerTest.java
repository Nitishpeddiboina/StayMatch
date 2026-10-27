package com.staymatch.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.staymatch.config.SecurityConfig;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.DatabaseUserDetailsService;
import com.staymatch.service.PropertyPhotoService;
import com.staymatch.service.PropertyPhotoService.StoredImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropertyPhotoController.class)
@Import(SecurityConfig.class)
class PropertyPhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyPhotoService photoService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void activePropertyPhotosArePubliclyServedAsImagesWithNosniff() throws Exception {
        when(photoService.getPublicPhoto(8L, 9L)).thenReturn(new StoredImage(new byte[] {1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/v1/properties/8/photos/9"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void nonGetRequestsRemainProtected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/properties/8/photos/9").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
