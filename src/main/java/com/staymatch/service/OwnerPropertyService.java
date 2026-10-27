package com.staymatch.service;

import com.staymatch.dto.OwnerPropertyRequest;
import com.staymatch.dto.OwnerPropertyResponse;
import com.staymatch.entity.Amenity;
import com.staymatch.entity.Property;
import com.staymatch.entity.Role;
import com.staymatch.repository.AmenityRepository;
import com.staymatch.repository.PropertyRepository;
import com.staymatch.security.StayMatchUserDetails;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerPropertyService {

    private static final String NOT_FOUND_MESSAGE = "Property not found.";

    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;

    public OwnerPropertyService(PropertyRepository propertyRepository, AmenityRepository amenityRepository) {
        this.propertyRepository = propertyRepository;
        this.amenityRepository = amenityRepository;
    }

    @Transactional
    public OwnerPropertyResponse create(StayMatchUserDetails owner, OwnerPropertyRequest request) {
        requireOwner(owner);
        Property property = Property.newListing();
        property.setOwnerId(owner.getId());
        property.setActive(true);
        property.setCreatedAt(LocalDateTime.now());
        applyRequest(property, request);
        return toResponse(propertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    public List<OwnerPropertyResponse> listMine(StayMatchUserDetails owner) {
        requireOwner(owner);
        return propertyRepository.findAllByOwnerIdOrderByIdDesc(owner.getId()).stream()
                .map(OwnerPropertyService::toResponse)
                .toList();
    }

    @Transactional
    public OwnerPropertyResponse update(StayMatchUserDetails owner, Long id, OwnerPropertyRequest request) {
        requireOwner(owner);
        Property property = findOwnedProperty(id, owner.getId());
        applyRequest(property, request);
        property.setUpdatedAt(LocalDateTime.now());
        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    public OwnerPropertyResponse deactivate(StayMatchUserDetails owner, Long id) {
        requireOwner(owner);
        Property property = findOwnedProperty(id, owner.getId());
        property.setActive(false);
        property.setUpdatedAt(LocalDateTime.now());
        return toResponse(propertyRepository.save(property));
    }

    private void applyRequest(Property property, OwnerPropertyRequest request) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        validateTypeFields(type, request);

        property.setTitle(request.title().trim());
        property.setType(type);
        property.setBhk(request.bhk());
        property.setSharingType(request.sharingType());
        property.setArea(request.area().trim());
        property.setCity(request.city().trim());
        property.setAddressLine(request.addressLine().trim());
        property.setContactPhone(normalizeIndianPhone(request.contactPhone()));
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());
        property.setMonthlyRent(request.monthlyRent());
        property.setDeposit(request.deposit() == null ? 0 : request.deposit());
        property.setGenderPreference(request.genderPreference() == null || request.genderPreference().isBlank()
                ? "ANY" : request.genderPreference().trim().toUpperCase(Locale.ROOT));
        property.setDescription(request.description() == null ? null : request.description().trim());
        property.setUpdatedAt(LocalDateTime.now());
        property.getAmenities().clear();
        property.getAmenities().addAll(resolveAmenities(request.amenities()));
    }

    private String normalizeIndianPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String input = value.trim();
        if (!input.matches("[+0-9() .-]+")) {
            throw new InvalidOwnerPropertyException("Contact phone must be a valid Indian phone number.");
        }
        boolean explicitCountryCode = input.startsWith("+");
        if (explicitCountryCode && !input.startsWith("+91")) {
            throw new InvalidOwnerPropertyException("Contact phone must use an Indian +91 number.");
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.startsWith("91") && digits.length() >= 12) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[1-9][0-9]{9}")) {
            throw new InvalidOwnerPropertyException("Contact phone must be a valid Indian phone number.");
        }
        return "+91" + digits;
    }

    private void validateTypeFields(String type, OwnerPropertyRequest request) {
        boolean valid = switch (type) {
            case "PG" -> request.sharingType() != null && request.bhk() == null;
            case "ROOM" -> request.sharingType() == null && request.bhk() == null;
            case "RENTAL_HOME" -> request.bhk() != null && request.sharingType() == null;
            default -> false;
        };
        if (!valid) {
            throw new InvalidOwnerPropertyException(
                    "PG requires sharingType; RENTAL_HOME requires bhk; ROOM uses neither field.");
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new InvalidOwnerPropertyException("latitude and longitude must be supplied together.");
        }
    }

    private Set<Amenity> resolveAmenities(Collection<String> requestedNames) {
        if (requestedNames == null || requestedNames.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedNames = requestedNames.stream()
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        List<Amenity> found = amenityRepository.findByNameIn(normalizedNames);
        if (found.size() != normalizedNames.size()) {
            throw new InvalidOwnerPropertyException("One or more amenities are not in the available catalog.");
        }
        return Set.copyOf(found);
    }

    private Property findOwnedProperty(Long id, Long ownerId) {
        return propertyRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND_MESSAGE));
    }

    private void requireOwner(StayMatchUserDetails owner) {
        if (owner == null || owner.getRole() != Role.OWNER) {
            throw new AccessDeniedException("Owner role is required.");
        }
    }

    private static OwnerPropertyResponse toResponse(Property property) {
        List<String> amenityNames = property.getAmenities().stream()
                .map(Amenity::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new OwnerPropertyResponse(
                property.getId(), property.getTitle(), property.getType(), property.getBhk(),
                property.getSharingType(), property.getArea(), property.getCity(), property.getAddressLine(),
                property.getContactPhone(),
                property.getLatitude(), property.getLongitude(), property.getMonthlyRent(), property.getDeposit(),
                property.getGenderPreference(), property.getActive(), property.getDescription(), amenityNames,
                property.getCreatedAt(), property.getUpdatedAt());
    }
}
