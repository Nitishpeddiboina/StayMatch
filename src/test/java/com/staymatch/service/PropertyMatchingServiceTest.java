package com.staymatch.service;

import com.staymatch.dto.PropertyMatchResponse;
import com.staymatch.entity.Amenity;
import com.staymatch.entity.Property;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyMatchingServiceTest {

    @Mock
    private EntityManager entityManager;

    private PropertyMatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new PropertyMatchingService(entityManager);
    }

    @Test
    void exactMatchesEarnFullPointsForSuppliedPreferences() {
        Property property = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                10000, "ANY", "WIFI", "AC");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), "Hyderabad", "Gachibowli", 10000, "pg", null, 3,
                "MALE", Set.of("wifi", "ac"));

        assertEquals(new BigDecimal("100.00"), matches.get(0).matchPercentage());
    }

    @Test
    void amenityPreferenceReceivesProportionalPartialCredit() {
        Property property = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                10000, "ANY", "WIFI");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), null, null, null, null, null, null,
                null, Set.of("WIFI", "AC"));

        assertEquals(new BigDecimal("50.00"), matches.get(0).matchPercentage());
    }

    @Test
    void overBudgetPropertyGetsReducedNonNegativeRentScore() {
        Property property = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                20000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), null, null, 10000, null, null, null, null, null);

        assertEquals(new BigDecimal("50.00"), matches.get(0).matchPercentage());
    }

    @Test
    void cityAndAreaAreScoredIndependentlyWithoutExcludingMismatches() {
        Property property = property(1L, "Hyderabad", "Kondapur", "ROOM", null, null,
                14000, "FEMALE");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), "Hyderabad", "Gachibowli", null, null, null, null, null, null);

        assertEquals(1, matches.size());
        assertEquals(new BigDecimal("40.00"), matches.get(0).matchPercentage());
    }

    @Test
    void excludesGenderIncompatiblePropertiesButAllowsAny() {
        Property incompatible = property(1L, "Hyderabad", "A", "PG", null, 3,
                10000, "FEMALE");
        Property compatible = property(2L, "Hyderabad", "B", "PG", null, 3,
                10000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(incompatible, compatible), null, null, null, null, null, null,
                "MALE", null);

        assertEquals(List.of(2L), matches.stream().map(PropertyMatchResponse::propertyId).toList());
        assertEquals(new BigDecimal("0.00"), matches.get(0).matchPercentage());
    }

    @Test
    void normalizesOverOnlyThePreferencesThatWereSupplied() {
        Property property = property(1L, "Hyderabad", "Kondapur", "ROOM", null, null,
                14000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), "Hyderabad", null, null, "PG", null, null, null, null);

        assertEquals(new BigDecimal("40.00"), matches.get(0).matchPercentage());
    }

    @Test
    void returnsNeutralZeroWhenNoScoredPreferencesAreSupplied() {
        Property property = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                10000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), " ", null, null, "", null, null, null, Set.of());

        assertEquals(new BigDecimal("0.00"), matches.get(0).matchPercentage());
    }

    @Test
    void layoutPointsAreCappedAtFifteenWhenBothBhkAndSharingAreRequested() {
        Property property = property(1L, "Hyderabad", "Gachibowli", "PG", 2, 3,
                10000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(property), null, null, null, null, 2, 1, null, null);

        assertEquals(new BigDecimal("50.00"), matches.get(0).matchPercentage());
    }

    @Test
    void scoresStayWithinZeroAndOneHundredAndOnlyActivePropertiesAreIncluded() {
        Property fullMatch = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                10000, "ANY");
        Property noMatch = property(2L, "Chennai", "Miyapur", "ROOM", 2, null,
                20000, "ANY");
        Property inactive = property(3L, "Hyderabad", "Gachibowli", "PG", null, 3,
                10000, "ANY");
        when(inactive.getActive()).thenReturn(false);

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(fullMatch, noMatch, inactive), "Hyderabad", "Gachibowli", null,
                "PG", null, null, null, null);

        assertEquals(2, matches.size());
        assertEquals(new BigDecimal("100.00"), matches.get(0).matchPercentage());
        assertEquals(new BigDecimal("0.00"), matches.get(1).matchPercentage());
        assertTrue(matches.stream().allMatch(match ->
                match.matchPercentage().compareTo(BigDecimal.ZERO) >= 0
                        && match.matchPercentage().compareTo(new BigDecimal("100.00")) <= 0));
    }

    @Test
    void breaksTiesByRentThenPropertyId() {
        Property higherRent = property(1L, "Hyderabad", "Gachibowli", "PG", null, 3,
                12000, "ANY");
        Property sameRentHigherId = property(4L, "Hyderabad", "Kondapur", "ROOM", null, null,
                8000, "ANY");
        Property sameRentLowerId = property(2L, "Hyderabad", "Miyapur", "ROOM", null, null,
                8000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(higherRent, sameRentHigherId, sameRentLowerId), null, null, null,
                null, null, null, null, null);

        assertEquals(List.of(2L, 4L, 1L), matches.stream().map(PropertyMatchResponse::propertyId).toList());
    }

    @Test
    void selectedRentRangeIncludesBothBoundariesAndExcludesOutOfRangeMatches() {
        Property atMinimum = property(1L, "Hyderabad", "Gachibowli", "PG", null, 2,
                5000, "ANY", "WIFI");
        Property insideRange = property(2L, "Hyderabad", "Gachibowli", "PG", null, 2,
                7500, "ANY", "WIFI");
        Property atMaximum = property(3L, "Hyderabad", "Gachibowli", "PG", null, 2,
                10000, "ANY", "WIFI");
        Property belowRange = property(4L, "Hyderabad", "Gachibowli", "PG", null, 2,
                4999, "ANY", "WIFI");
        Property aboveRange = property(5L, "Hyderabad", "Gachibowli", "PG", null, 2,
                10001, "ANY", "WIFI");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(atMinimum, insideRange, atMaximum, belowRange, aboveRange),
                "Hyderabad", "Gachibowli", 5000, 10000, "PG", null, 2,
                null, Set.of("WIFI"));

        assertEquals(List.of(1L, 2L, 3L), matches.stream().map(PropertyMatchResponse::propertyId).toList());
        assertTrue(matches.stream().allMatch(match -> match.monthlyRent() >= 5000 && match.monthlyRent() <= 10000));
    }

    @Test
    void rentRangeCombinesWithTypeAndLocationPreferencesWithoutChangingScoring() {
        Property desired = property(1L, "Hyderabad", "Gachibowli", "PG", null, 2,
                10000, "ANY");
        Property differentType = property(2L, "Hyderabad", "Gachibowli", "ROOM", null, null,
                10000, "ANY");
        Property wrongArea = property(3L, "Hyderabad", "Miyapur", "PG", null, 2,
                10000, "ANY");

        List<PropertyMatchResponse> matches = matchingService.scoreAndRank(
                List.of(desired, differentType, wrongArea), "Hyderabad", "Gachibowli",
                5000, 10000, "PG", null, 2, null, null);

        assertEquals(List.of(1L, 3L, 2L), matches.stream().map(PropertyMatchResponse::propertyId).toList());
        assertEquals(List.of(new BigDecimal("100.00"), new BigDecimal("81.25"), new BigDecimal("62.50")),
                matches.stream().map(PropertyMatchResponse::matchPercentage).toList());
    }

    private Property property(
            Long id,
            String city,
            String area,
            String type,
            Integer bhk,
            Integer sharingType,
            Integer rent,
            String genderPreference,
        String... amenityNames) {
        Property property = mock(Property.class);
        Set<Amenity> propertyAmenities = amenities(amenityNames);
        lenient().when(property.getId()).thenReturn(id);
        lenient().when(property.getActive()).thenReturn(true);
        lenient().when(property.getCity()).thenReturn(city);
        lenient().when(property.getArea()).thenReturn(area);
        lenient().when(property.getType()).thenReturn(type);
        lenient().when(property.getBhk()).thenReturn(bhk);
        lenient().when(property.getSharingType()).thenReturn(sharingType);
        lenient().when(property.getMonthlyRent()).thenReturn(rent);
        lenient().when(property.getGenderPreference()).thenReturn(genderPreference);
        lenient().when(property.getAmenities()).thenReturn(propertyAmenities);
        return property;
    }

    private Set<Amenity> amenities(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> {
                    Amenity amenity = mock(Amenity.class);
                    lenient().when(amenity.getName()).thenReturn(name);
                    return amenity;
                })
                .collect(java.util.stream.Collectors.toSet());
    }
}
