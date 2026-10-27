package com.staymatch.service;

import com.staymatch.dto.PropertyMatchResponse;
import com.staymatch.dto.PropertyPhotoResponse;
import com.staymatch.entity.Amenity;
import com.staymatch.entity.Property;
import com.staymatch.entity.PropertyPhoto;
import com.staymatch.repository.PropertyPhotoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scores active properties against preferences supplied for a single request.
 * Gender is an eligibility rule, not a score category: incompatible properties
 * are excluded, so gender does not contribute to the normalization denominator.
 * Only supplied preferences contribute to the denominator. If none are supplied,
 * the neutral score is zero. When both BHK and sharing are supplied, each receives
 * half of the layout category's 15 points.
 */
@Service
public class PropertyMatchingService {

    private static final int MAX_RENT_WEIGHT = 25;
    private static final int AREA_WEIGHT = 15;
    private static final int TYPE_WEIGHT = 15;
    private static final int LAYOUT_WEIGHT = 15;
    private static final int CITY_WEIGHT = 10;
    private static final int AMENITIES_WEIGHT = 10;
    private static final Set<String> VALID_REQUESTED_GENDERS = Set.of("MALE", "FEMALE");

    private final EntityManager entityManager;
    private final PropertyPhotoRepository propertyPhotoRepository;

    public PropertyMatchingService(EntityManager entityManager) {
        this(entityManager, null);
    }

    @Autowired
    public PropertyMatchingService(EntityManager entityManager, PropertyPhotoRepository propertyPhotoRepository) {
        this.entityManager = entityManager;
        this.propertyPhotoRepository = propertyPhotoRepository;
    }

    /**
     * Finds and ranks active properties. City, area, type, layout, and rent are
     * scored preferences; only active status and gender compatibility affect eligibility.
     */
    public List<PropertyMatchResponse> findMatches(
            String city,
            String area,
            Integer maxRent,
            String type,
            Integer bhk,
            Integer sharingType,
            String requestedGender,
            Collection<String> requestedAmenityNames) {
        return findMatches(city, area, null, maxRent, type, bhk, sharingType,
                requestedGender, requestedAmenityNames);
    }

    public List<PropertyMatchResponse> findMatches(
            String city,
            String area,
            Integer minRent,
            Integer maxRent,
            String type,
            Integer bhk,
            Integer sharingType,
            String requestedGender,
            Collection<String> requestedAmenityNames) {
        String normalizedGender = normalizeRequestedGender(requestedGender);
        List<Property> candidates = findActiveCandidates(normalizedGender);
        List<PropertyMatchResponse> matches = scoreAndRank(candidates, city, area, minRent, maxRent,
                type, bhk, sharingType, normalizedGender, requestedAmenityNames);
        return attachPhotos(matches);
    }

    private List<Property> findActiveCandidates(String requestedGender) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Property> criteriaQuery = criteriaBuilder.createQuery(Property.class);
        Root<Property> property = criteriaQuery.from(Property.class);
        property.fetch("amenities", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.isTrue(property.get("active")));
        if (requestedGender != null) {
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.equal(property.get("genderPreference"), "ANY"),
                    criteriaBuilder.equal(property.get("genderPreference"), requestedGender)));
        }

        criteriaQuery.select(property).distinct(true);
        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(criteriaQuery).getResultList();
    }

    List<PropertyMatchResponse> scoreAndRank(
            List<Property> candidates,
            String city,
            String area,
            Integer maxRent,
            String type,
            Integer bhk,
            Integer sharingType,
            String requestedGender,
            Collection<String> requestedAmenityNames) {
        return scoreAndRank(candidates, city, area, null, maxRent, type, bhk, sharingType,
                requestedGender, requestedAmenityNames);
    }

    List<PropertyMatchResponse> scoreAndRank(
            List<Property> candidates,
            String city,
            String area,
            Integer minRent,
            Integer maxRent,
            String type,
            Integer bhk,
            Integer sharingType,
            String requestedGender,
            Collection<String> requestedAmenityNames) {
        String cityPreference = normalizeText(city);
        String areaPreference = normalizeText(area);
        String typePreference = normalizeText(type);
        String genderPreference = normalizeRequestedGender(requestedGender);
        Set<String> amenityPreferences = normalizeAmenityNames(requestedAmenityNames);

        return candidates.stream()
                .filter(property -> Boolean.TRUE.equals(property.getActive()))
                .filter(property -> isGenderCompatible(property, genderPreference))
                .filter(property -> isWithinSelectedRentRange(property, minRent, maxRent))
                .map(property -> scoreProperty(property, cityPreference, areaPreference, maxRent,
                        typePreference, bhk, sharingType, amenityPreferences))
                .sorted(Comparator.comparing(PropertyMatchResponse::matchPercentage).reversed()
                        .thenComparing(PropertyMatchResponse::monthlyRent,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PropertyMatchResponse::propertyId,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private boolean isWithinSelectedRentRange(Property property, Integer minRent, Integer maxRent) {
        if (minRent == null) {
            return true;
        }
        Integer rent = property.getMonthlyRent();
        return rent != null && rent >= minRent && maxRent != null && rent <= maxRent;
    }

    private List<PropertyMatchResponse> attachPhotos(List<PropertyMatchResponse> matches) {
        if (matches.isEmpty() || propertyPhotoRepository == null) {
            return matches;
        }
        List<Long> propertyIds = matches.stream().map(PropertyMatchResponse::propertyId).toList();
        Map<Long, List<PropertyPhotoResponse>> photosByProperty = propertyPhotoRepository
                .findAllByPropertyIdInOrderByPropertyIdAscIdAsc(propertyIds).stream()
                .collect(Collectors.groupingBy(PropertyPhoto::getPropertyId, LinkedHashMap::new,
                        Collectors.mapping(photo -> new PropertyPhotoResponse(
                                photo.getId(), "/api/v1/properties/" + photo.getPropertyId()
                                        + "/photos/" + photo.getId(),
                                photo.getMediaType(), photo.getSizeBytes(), photo.getUploadedAt()),
                                Collectors.toList())));
        return matches.stream().map(match -> new PropertyMatchResponse(
                match.propertyId(), match.title(), match.type(), match.bhk(), match.sharingType(),
                match.area(), match.city(), match.addressLine(), match.contactPhone(), match.latitude(),
                match.longitude(), match.monthlyRent(), match.deposit(), match.genderPreference(),
                match.description(), match.amenities(), match.matchPercentage(),
                photosByProperty.getOrDefault(match.propertyId(), List.of())))
                .toList();
    }

    private PropertyMatchResponse scoreProperty(
            Property property,
            String city,
            String area,
            Integer maxRent,
            String type,
            Integer bhk,
            Integer sharingType,
            Set<String> requestedAmenities) {
        double earnedPoints = 0;
        int possiblePoints = 0;

        if (maxRent != null) {
            possiblePoints += MAX_RENT_WEIGHT;
            Integer monthlyRent = property.getMonthlyRent();
            if (monthlyRent != null && monthlyRent > 0) {
                // Over-budget rent receives proportional points and can never be negative.
                earnedPoints += monthlyRent <= maxRent
                        ? MAX_RENT_WEIGHT
                        : Math.max(0, (double) MAX_RENT_WEIGHT * maxRent / monthlyRent);
            }
        }
        if (area != null) {
            possiblePoints += AREA_WEIGHT;
            if (matches(property.getArea(), area)) {
                earnedPoints += AREA_WEIGHT;
            }
        }
        if (type != null) {
            possiblePoints += TYPE_WEIGHT;
            if (matches(property.getType(), type)) {
                earnedPoints += TYPE_WEIGHT;
            }
        }
        int layoutPreferenceCount = (bhk == null ? 0 : 1) + (sharingType == null ? 0 : 1);
        if (layoutPreferenceCount > 0) {
            possiblePoints += LAYOUT_WEIGHT;
            double pointsPerLayoutPreference = (double) LAYOUT_WEIGHT / layoutPreferenceCount;
            if (bhk != null && bhk.equals(property.getBhk())) {
                earnedPoints += pointsPerLayoutPreference;
            }
            if (sharingType != null && sharingType.equals(property.getSharingType())) {
                earnedPoints += pointsPerLayoutPreference;
            }
        }
        if (city != null) {
            possiblePoints += CITY_WEIGHT;
            if (matches(property.getCity(), city)) {
                earnedPoints += CITY_WEIGHT;
            }
        }
        if (!requestedAmenities.isEmpty()) {
            possiblePoints += AMENITIES_WEIGHT;
            Set<String> propertyAmenities = getPropertyAmenityNames(property);
            long matchingAmenities = requestedAmenities.stream().filter(propertyAmenities::contains).count();
            earnedPoints += (double) AMENITIES_WEIGHT * matchingAmenities / requestedAmenities.size();
        }

        BigDecimal percentage = possiblePoints == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(Math.min(100, Math.max(0, earnedPoints * 100 / possiblePoints)))
                        .setScale(2, RoundingMode.HALF_UP);

        return new PropertyMatchResponse(
                property.getId(),
                property.getTitle(),
                property.getType(),
                property.getBhk(),
                property.getSharingType(),
                property.getArea(),
                property.getCity(),
                property.getAddressLine(),
                property.getContactPhone(),
                property.getLatitude(),
                property.getLongitude(),
                property.getMonthlyRent(),
                property.getDeposit(),
                property.getGenderPreference(),
                property.getDescription(),
                getPropertyAmenityNames(property).stream().sorted().toList(),
                percentage,
                List.of());
    }

    private boolean isGenderCompatible(Property property, String requestedGender) {
        if (requestedGender == null) {
            return true;
        }
        String propertyGender = normalizeText(property.getGenderPreference());
        return propertyGender != null
                && ("ANY".equalsIgnoreCase(propertyGender)
                || requestedGender.equalsIgnoreCase(propertyGender));
    }

    private Set<String> getPropertyAmenityNames(Property property) {
        if (property.getAmenities() == null) {
            return Set.of();
        }
        return property.getAmenities().stream()
                .map(Amenity::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Set<String> normalizeAmenityNames(Collection<String> amenityNames) {
        if (amenityNames == null) {
            return Set.of();
        }
        return amenityNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRequestedGender(String gender) {
        String normalized = normalizeText(gender);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!VALID_REQUESTED_GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("requestedGender must be MALE or FEMALE.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean matches(String propertyValue, String preference) {
        return propertyValue != null && propertyValue.trim().equalsIgnoreCase(preference);
    }
}
