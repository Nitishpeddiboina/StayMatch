package com.staymatch.controller;

import com.staymatch.entity.Property;
import com.staymatch.dto.PropertyMatchResponse;
import com.staymatch.service.PropertyMatchingService;
import com.staymatch.service.PropertyService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private static final Set<String> VALID_PROPERTY_TYPES = Set.of("PG", "ROOM", "RENTAL_HOME");

    private final PropertyService propertyService;
    private final PropertyMatchingService propertyMatchingService;

    public PropertyController(PropertyService propertyService, PropertyMatchingService propertyMatchingService) {
        this.propertyService = propertyService;
        this.propertyMatchingService = propertyMatchingService;
    }

    @GetMapping
    public List<Property> getAllProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minRent,
            @RequestParam(required = false) Integer maxRent,
            @RequestParam(required = false) Integer bhk,
            @RequestParam(required = false) Integer sharingType) {
        validateFilters(type, minRent, maxRent, bhk, sharingType);
        if (minRent == null) {
            return propertyService.searchProperties(city, area, type, maxRent, bhk, sharingType);
        }
        return propertyService.searchProperties(city, area, type, minRent, maxRent, bhk, sharingType);
    }

    @GetMapping("/{id}")
    public Property getPropertyById(@PathVariable Long id) {
        return propertyService.getPropertyById(id);
    }

    @GetMapping("/matches")
    public List<PropertyMatchResponse> getPropertyMatches(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer maxRent,
            @RequestParam(required = false) Integer minRent,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer bhk,
            @RequestParam(required = false) Integer sharingType,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String amenities) {
        validateFilters(type, minRent, maxRent, bhk, sharingType);
        String normalizedGender = normalizeGender(gender);
        List<String> amenityNames = parseAmenities(amenities);
        if (minRent == null) {
            return propertyMatchingService.findMatches(
                    normalizeOptionalText(city), normalizeOptionalText(area), maxRent,
                    normalizeOptionalText(type) == null ? null : type.trim().toUpperCase(Locale.ROOT),
                    bhk, sharingType, normalizedGender, amenityNames);
        }
        return propertyMatchingService.findMatches(
                normalizeOptionalText(city), normalizeOptionalText(area), minRent, maxRent,
                normalizeOptionalText(type) == null ? null : type.trim().toUpperCase(Locale.ROOT),
                bhk, sharingType, normalizedGender, amenityNames);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleBadRequest(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleInvalidParameterType(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body("Invalid value for '" + exception.getName() + "': expected an integer.");
    }

    private void validateFilters(String type, Integer minRent, Integer maxRent, Integer bhk, Integer sharingType) {
        if (type != null && !type.isBlank()
                && !VALID_PROPERTY_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "type must be one of PG, ROOM, or RENTAL_HOME.");
        }
        if (maxRent != null && maxRent <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRent must be a positive integer.");
        }
        if (minRent != null && minRent <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRent must be a positive integer.");
        }
        if (minRent != null && maxRent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRent is required when minRent is supplied.");
        }
        if (minRent != null && maxRent != null && minRent > maxRent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRent must not exceed maxRent.");
        }
        if (bhk != null && (bhk < 1 || bhk > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bhk must be between 1 and 3.");
        }
        if (sharingType != null && (sharingType < 1 || sharingType > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sharingType must be between 1 and 3.");
        }
    }

    private String normalizeGender(String gender) {
        String normalized = normalizeOptionalText(gender);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("MALE", "FEMALE").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gender must be MALE or FEMALE.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> parseAmenities(String amenities) {
        if (amenities == null || amenities.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : amenities.split(",")) {
            String normalized = normalizeOptionalText(name);
            if (normalized != null) {
                names.add(normalized.toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }
}
