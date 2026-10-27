package com.staymatch.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Property details and the normalized percentage produced by property matching.
 * Owner account data and JPA entities are intentionally not exposed.
 */
public record PropertyMatchResponse(
        Long propertyId,
        String title,
        String type,
        Integer bhk,
        Integer sharingType,
        String area,
        String city,
        String addressLine,
        String contactPhone,
        Double latitude,
        Double longitude,
        Integer monthlyRent,
        Integer deposit,
        String genderPreference,
        String description,
        List<String> amenities,
        BigDecimal matchPercentage,
        List<PropertyPhotoResponse> photos) {
}
