package com.staymatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.staymatch.dto.OwnerPropertyRequest;
import com.staymatch.dto.OwnerPropertyResponse;
import com.staymatch.entity.Amenity;
import com.staymatch.entity.Property;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.repository.AmenityRepository;
import com.staymatch.repository.PropertyRepository;
import com.staymatch.security.StayMatchUserDetails;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerPropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private AmenityRepository amenityRepository;

    private OwnerPropertyService service;
    private StayMatchUserDetails owner;

    @BeforeEach
    void setUp() {
        service = new OwnerPropertyService(propertyRepository, amenityRepository);
        User user = new User("Owner", "owner@example.test", "unused-hash", Role.OWNER);
        user.setId(42L);
        owner = new StayMatchUserDetails(user);
    }

    @Test
    void createAssignsAuthenticatedOwnerAndActivatesListing() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(owner, pgRequest(9500));

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertEquals(42L, captor.getValue().getOwnerId());
        assertEquals("PG", captor.getValue().getType());
        assertEquals(9500, captor.getValue().getMonthlyRent());
        assertEquals(true, captor.getValue().getActive());
    }

    @Test
    void listOnlyQueriesAuthenticatedOwnersPropertiesIncludingInactiveRows() {
        Property inactive = property(8L, 42L, false);
        when(propertyRepository.findAllByOwnerIdOrderByIdDesc(42L)).thenReturn(List.of(inactive));

        List<OwnerPropertyResponse> results = service.listMine(owner);

        assertEquals(1, results.size());
        assertFalse(results.get(0).active());
        verify(propertyRepository).findAllByOwnerIdOrderByIdDesc(42L);
    }

    @Test
    void cannotEditAnotherOwnersProperty() {
        when(propertyRepository.findByIdAndOwnerId(9L, 42L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.update(owner, 9L, pgRequest(9500)));

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void cannotDeactivateAnotherOwnersProperty() {
        when(propertyRepository.findByIdAndOwnerId(9L, 42L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.deactivate(owner, 9L));

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void ownerCanEditOwnPropertyWithoutChangingItsOwnerOrActiveStatus() {
        Property existing = property(8L, 42L, false);
        existing.setTitle("Original title");
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(existing)).thenReturn(existing);

        OwnerPropertyResponse result = service.update(owner, 8L, pgRequest(11000));

        assertEquals(8L, result.id());
        assertEquals("Shared PG", result.title());
        assertEquals(42L, existing.getOwnerId());
        assertEquals(11000, existing.getMonthlyRent());
        assertFalse(existing.getActive());
        verify(propertyRepository).findByIdAndOwnerId(8L, 42L);
    }

    @Test
    void updateReplacesAmenitiesInTheExistingManagedCollection() {
        Property existing = property(8L, 42L, true);
        Amenity oldAmenity = amenity("WIFI");
        Amenity replacementAmenity = amenity("AC");
        existing.getAmenities().add(oldAmenity);
        Set<Amenity> managedCollection = existing.getAmenities();
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.of(existing));
        when(amenityRepository.findByNameIn(Set.of("AC"))).thenReturn(List.of(replacementAmenity));
        when(propertyRepository.save(existing)).thenReturn(existing);

        OwnerPropertyResponse result = service.update(owner, 8L, pgRequest(11000, Set.of("AC")));

        org.junit.jupiter.api.Assertions.assertSame(managedCollection, existing.getAmenities());
        assertEquals(Set.of(replacementAmenity), existing.getAmenities());
        assertEquals(List.of("AC"), result.amenities());
        verify(amenityRepository).findByNameIn(Set.of("AC"));
    }

    @Test
    void updateWithEmptyAmenitiesClearsTheExistingManagedCollection() {
        Property existing = property(8L, 42L, true);
        existing.getAmenities().add(amenity("WIFI"));
        Set<Amenity> managedCollection = existing.getAmenities();
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(existing)).thenReturn(existing);

        OwnerPropertyResponse result = service.update(owner, 8L, pgRequest(11000, Set.of()));

        org.junit.jupiter.api.Assertions.assertSame(managedCollection, existing.getAmenities());
        assertEquals(Set.of(), existing.getAmenities());
        assertEquals(List.of(), result.amenities());
        verify(amenityRepository, never()).findByNameIn(any());
    }

    @Test
    void ownerCanDeactivateOwnPropertyWithoutDeletingIt() {
        Property existing = property(8L, 42L, true);
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(existing)).thenReturn(existing);

        OwnerPropertyResponse result = service.deactivate(owner, 8L);

        assertFalse(result.active());
        assertEquals(42L, existing.getOwnerId());
        verify(propertyRepository).findByIdAndOwnerId(8L, 42L);
        verify(propertyRepository).save(existing);
    }

    @Test
    void rejectsTypeSpecificFieldsThatDoNotMatchSchemaConventions() {
        OwnerPropertyRequest rentalWithoutBhk = new OwnerPropertyRequest(
                "Home", "RENTAL_HOME", null, null, "Miyapur", "Hyderabad", "Address",
                null, null, null, 20000, 0, "ANY", null, null);

        assertThrows(InvalidOwnerPropertyException.class, () -> service.create(owner, rentalWithoutBhk));
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void normalizesIndianContactPhoneToE164() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OwnerPropertyResponse response = service.create(owner, pgRequestWithPhone("+91 (98765) 43210"));

        assertEquals("+919876543210", response.contactPhone());
    }

    @Test
    void acceptsAndNormalizesAnIndianLandline() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OwnerPropertyResponse response = service.create(owner, pgRequestWithPhone("040 1234 5678"));

        assertEquals("+914012345678", response.contactPhone());
    }

    @Test
    void rejectsInvalidIndianContactPhone() {
        assertThrows(InvalidOwnerPropertyException.class,
                () -> service.create(owner, pgRequestWithPhone("+1 202 555 0100")));
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void directServiceCallRejectsNonOwnerPrincipal() {
        User seekerUser = new User("Seeker", "seeker@example.test", "unused-hash", Role.SEEKER);
        seekerUser.setId(43L);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.create(new StayMatchUserDetails(seekerUser), pgRequest(9500)));
        verifyNoInteractions(propertyRepository);
    }

    private OwnerPropertyRequest pgRequest(int rent) {
        return pgRequest(rent, null);
    }

    private OwnerPropertyRequest pgRequest(int rent, Set<String> amenities) {
        return new OwnerPropertyRequest("Shared PG", "PG", null, 2, "Gachibowli", "Hyderabad",
                "Fictional address", null, null, null, rent, 9000, "ANY", "Fictional listing", amenities);
    }

    private OwnerPropertyRequest pgRequestWithPhone(String phone) {
        return new OwnerPropertyRequest("Shared PG", "PG", null, 2, "Gachibowli", "Hyderabad",
                "Fictional address", phone, null, null, 9500, 9000, "ANY", "Fictional listing", Set.of());
    }

    private Amenity amenity(String name) {
        Amenity amenity = new TestAmenity();
        amenity.setName(name);
        return amenity;
    }

    private static final class TestAmenity extends Amenity {
    }

    private Property property(Long id, Long ownerId, boolean active) {
        Property property = Property.newListing();
        property.setId(id);
        property.setOwnerId(ownerId);
        property.setTitle("Listing");
        property.setType("PG");
        property.setArea("Gachibowli");
        property.setCity("Hyderabad");
        property.setAddressLine("Fictional address");
        property.setMonthlyRent(9500);
        property.setDeposit(9000);
        property.setGenderPreference("ANY");
        property.setActive(active);
        return property;
    }
}
