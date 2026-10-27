package com.staymatch.service;

import com.staymatch.entity.Property;
import com.staymatch.repository.PropertyRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    @Test
    void getAllPropertiesSpecificationContainsOnlyActivePredicateWhenNoFiltersAreSupplied() {
        when(propertyRepository.findAll(any(Specification.class))).thenReturn(List.of());

        propertyService.getAllProperties();

        Specification<Property> specification = captureSpecification();
        Root<Property> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Boolean> activePath = mock(Path.class);
        Predicate activePredicate = mock(Predicate.class);
        when(root.<Boolean>get("active")).thenReturn(activePath);
        when(criteriaBuilder.isTrue(activePath)).thenReturn(activePredicate);

        assertSame(activePredicate, specification.toPredicate(root, query, criteriaBuilder));

        verify(root).<Boolean>get("active");
        verifyNoMoreInteractions(root);
        verify(criteriaBuilder).isTrue(activePath);
        verifyNoMoreInteractions(criteriaBuilder);
    }

    @Test
    void combinedFiltersAreAddedAsCriteriaPredicates() {
        when(propertyRepository.findAll(any(Specification.class))).thenReturn(List.of());

        propertyService.searchProperties(" Hyderabad ", " Gachibowli ", " pg ", 12000, 2, 3);

        Specification<Property> specification = captureSpecification();
        Root<Property> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(predicate);

        specification.toPredicate(root, query, criteriaBuilder);

        verify(root).<Boolean>get("active");
        verify(root).<String>get("city");
        verify(root).<String>get("area");
        verify(root).<String>get("type");
        verify(root).<Integer>get("monthlyRent");
        verify(root).<Integer>get("bhk");
        verify(root).<Integer>get("sharingType");
        verify(criteriaBuilder).like(any(), eq("%hyderabad%"));
        verify(criteriaBuilder).like(any(), eq("%gachibowli%"));
        verify(criteriaBuilder).equal(any(), eq("PG"));
        verify(criteriaBuilder).lessThanOrEqualTo(any(), eq(12000));
        verify(criteriaBuilder).equal(any(), eq(2));
        verify(criteriaBuilder).equal(any(), eq(3));
        verify(criteriaBuilder, times(6)).and(nullable(Predicate.class), nullable(Predicate.class));
    }

    @Test
    void minimumAndMaximumRentAreAddedAsInclusiveCriteriaPredicates() {
        when(propertyRepository.findAll(any(Specification.class))).thenReturn(List.of());

        propertyService.searchProperties("Hyderabad", "Gachibowli", "PG", 5000, 10000, null, null);

        Specification<Property> specification = captureSpecification();
        Root<Property> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(predicate);

        specification.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(5000));
        verify(criteriaBuilder).lessThanOrEqualTo(any(), eq(10000));
        verify(root, org.mockito.Mockito.times(2)).<Integer>get("monthlyRent");
    }

    @SuppressWarnings("unchecked")
    private Specification<Property> captureSpecification() {
        ArgumentCaptor<Specification<Property>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(propertyRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
