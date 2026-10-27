package com.staymatch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Mutable listing fields accepted for owner create and full update requests. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnerPropertyRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Pattern(regexp = "(?i)PG|ROOM|RENTAL_HOME") String type,
        @Min(1) @Max(3) Integer bhk,
        @Min(1) @Max(3) Integer sharingType,
        @NotBlank @Size(max = 100) String area,
        @NotBlank @Size(max = 100) String city,
        @NotBlank String addressLine,
        @Size(max = 32) @Pattern(regexp = "^[+0-9() .-]*$") String contactPhone,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Positive Integer monthlyRent,
        @PositiveOrZero Integer deposit,
        @Pattern(regexp = "(?i)MALE|FEMALE|ANY") String genderPreference,
        String description,
        @Size(max = 50) Set<@NotBlank @Size(max = 50) String> amenities) {
}
