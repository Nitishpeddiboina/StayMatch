package com.staymatch.controller;

import com.staymatch.dto.PropertyMatchResponse;
import com.staymatch.service.PropertyMatchingService;
import com.staymatch.service.PropertyService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PropertyControllerTest {

    @Mock
    private PropertyService propertyService;

    @Mock
    private PropertyMatchingService propertyMatchingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new PropertyController(propertyService, propertyMatchingService)).build();
    }

    @Test
    void getAllPropertiesPassesOptionalFiltersToService() throws Exception {
        when(propertyService.searchProperties("Hyderabad", "Gachibowli", "pg", 9500, 2, 3))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/properties")
                        .param("city", "Hyderabad")
                        .param("area", "Gachibowli")
                        .param("type", "pg")
                        .param("maxRent", "9500")
                        .param("bhk", "2")
                        .param("sharingType", "3"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(propertyService).searchProperties("Hyderabad", "Gachibowli", "pg", 9500, 2, 3);
    }

    @Test
    void getAllPropertiesPassesInclusiveRentRangeWithOtherFilters() throws Exception {
        when(propertyService.searchProperties("Hyderabad", "Gachibowli", "PG", 5000, 10000, null, null))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/properties")
                        .param("city", "Hyderabad").param("area", "Gachibowli").param("type", "PG")
                        .param("minRent", "5000").param("maxRent", "10000"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(propertyService).searchProperties("Hyderabad", "Gachibowli", "PG", 5000, 10000, null, null);
    }

    @Test
    void rejectsUnsupportedTypeWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/properties").param("type", "villa"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("type must be one of PG, ROOM, or RENTAL_HOME."));
    }

    @Test
    void rejectsNonPositiveMaxRentWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/properties").param("maxRent", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("maxRent must be a positive integer."));
    }

    @Test
    void rejectsUnsupportedBhkAndSharingTypeWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/properties").param("bhk", "4"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties").param("sharingType", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonIntegerParameterWithClearBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/properties").param("maxRent", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for 'maxRent': expected an integer."));
    }

    @Test
    void matchesPropertiesWithValidatedAndNormalizedPreferences() throws Exception {
        PropertyMatchResponse match = new PropertyMatchResponse(7L, "Home", "PG", 2, 3,
                "Gachibowli", "Hyderabad", "Street", "+919876543210", null, null, 9000, null,
                "ANY", null, java.util.List.of("WIFI"), new BigDecimal("95.00"),
                java.util.List.of(new com.staymatch.dto.PropertyPhotoResponse(12L,
                        "/api/v1/properties/7/photos/12", "image/jpeg", 4096L,
                        java.time.LocalDateTime.parse("2025-01-01T12:00:00"))));
        when(propertyMatchingService.findMatches("Hyderabad", "Gachibowli", 10000, "PG",
                2, 3, "FEMALE", java.util.List.of("WIFI", "AC"))).thenReturn(java.util.List.of(match));

        mockMvc.perform(get("/api/v1/properties/matches")
                        .param("city", " Hyderabad ").param("area", " Gachibowli ")
                        .param("maxRent", "10000").param("type", " pg ")
                        .param("bhk", "2").param("sharingType", "3").param("gender", " female ")
                        .param("amenities", " wifi, ,AC "))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].propertyId").value(7))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].contactPhone").value("+919876543210"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].matchPercentage").value(95.00))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].photos[0].url").value("/api/v1/properties/7/photos/12"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].photos[0].contentType").value("image/jpeg"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].photos[0].storageKey").doesNotExist());

        verify(propertyMatchingService).findMatches("Hyderabad", "Gachibowli", 10000, "PG",
                2, 3, "FEMALE", java.util.List.of("WIFI", "AC"));
    }

    @Test
    void matchesAcceptRentRangeTogetherWithTypeAndArea() throws Exception {
        when(propertyMatchingService.findMatches("Hyderabad", "Gachibowli", 5000, 10000,
                "PG", null, null, null, java.util.List.of())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/properties/matches")
                        .param("city", "Hyderabad").param("area", "Gachibowli")
                        .param("type", "pg").param("minRent", "5000").param("maxRent", "10000"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(propertyMatchingService).findMatches("Hyderabad", "Gachibowli", 5000, 10000,
                "PG", null, null, null, java.util.List.of());
    }

    @Test
    void rejectsInvalidRentRanges() throws Exception {
        mockMvc.perform(get("/api/v1/properties/matches").param("minRent", "10000").param("maxRent", "5000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/matches").param("minRent", "5000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/matches").param("minRent", "-1").param("maxRent", "5000"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(propertyMatchingService);
    }

    @Test
    void acceptsAllMatchingParametersAsOptional() throws Exception {
        when(propertyMatchingService.findMatches(null, null, null, null, null, null, null, java.util.List.of()))
                .thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/v1/properties/matches"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(propertyMatchingService).findMatches(null, null, null, null, null, null, null, java.util.List.of());
    }

    @Test
    void rejectsInvalidMatchingInputs() throws Exception {
        mockMvc.perform(get("/api/v1/properties/matches").param("maxRent", "0"))
                .andExpect(status().isBadRequest()).andExpect(content().string("maxRent must be a positive integer."));
        mockMvc.perform(get("/api/v1/properties/matches").param("type", "villa"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/matches").param("bhk", "4"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/matches").param("sharingType", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/matches").param("gender", "ANY"))
                .andExpect(status().isBadRequest()).andExpect(content().string("gender must be MALE or FEMALE."));
        mockMvc.perform(get("/api/v1/properties/matches").param("maxRent", "abc"))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid value for 'maxRent': expected an integer."));
        verifyNoInteractions(propertyMatchingService);
    }
}
