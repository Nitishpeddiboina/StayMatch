package com.staymatch.service;

import com.staymatch.entity.Property;
import com.staymatch.repository.PropertyRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public List<Property> getAllProperties() {
        return searchProperties(null, null, null, null, null, null);
    }

    public List<Property> searchProperties(
            String city,
            String area,
            String type,
            Integer maxRent,
            Integer bhk,
            Integer sharingType) {
        return searchProperties(city, area, type, null, maxRent, bhk, sharingType);
    }

    public List<Property> searchProperties(
            String city,
            String area,
            String type,
            Integer minRent,
            Integer maxRent,
            Integer bhk,
            Integer sharingType) {
        String cityFilter = normalizePartialFilter(city);
        String areaFilter = normalizePartialFilter(area);
        String typeFilter = normalizeTypeFilter(type);

        Specification<Property> specification = (root, query, criteriaBuilder) -> {
            Predicate activeProperty = criteriaBuilder.isTrue(root.get("active"));
            Predicate result = activeProperty;

            if (cityFilter != null) {
                Predicate cityMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("city")),
                        "%" + cityFilter.toLowerCase(Locale.ROOT) + "%");
                result = criteriaBuilder.and(result, cityMatches);
            }
            if (areaFilter != null) {
                Predicate areaMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("area")),
                        "%" + areaFilter.toLowerCase(Locale.ROOT) + "%");
                result = criteriaBuilder.and(result, areaMatches);
            }
            if (typeFilter != null) {
                Predicate typeMatches = criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("type")),
                        typeFilter);
                result = criteriaBuilder.and(result, typeMatches);
            }
            if (minRent != null) {
                Predicate rentMatches = criteriaBuilder.greaterThanOrEqualTo(
                        root.get("monthlyRent"), minRent);
                result = criteriaBuilder.and(result, rentMatches);
            }
            if (maxRent != null) {
                Predicate rentMatches = criteriaBuilder.lessThanOrEqualTo(
                        root.get("monthlyRent"),
                        maxRent);
                result = criteriaBuilder.and(result, rentMatches);
            }
            if (bhk != null) {
                Predicate bhkMatches = criteriaBuilder.equal(root.get("bhk"), bhk);
                result = criteriaBuilder.and(result, bhkMatches);
            }
            if (sharingType != null) {
                Predicate sharingMatches = criteriaBuilder.equal(root.get("sharingType"), sharingType);
                result = criteriaBuilder.and(result, sharingMatches);
            }

            return result;
        };

        return propertyRepository.findAll(specification);
    }

    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Property not found with id: " + id));
    }

    private String normalizePartialFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeTypeFilter(String value) {
        String normalized = normalizePartialFilter(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
