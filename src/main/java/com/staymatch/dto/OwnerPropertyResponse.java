package com.staymatch.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Safe listing details returned to its owner. No User entity or credentials are serialized. */
public record OwnerPropertyResponse(
        Long id,
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
        Boolean active,
        String description,
        List<String> amenities,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
