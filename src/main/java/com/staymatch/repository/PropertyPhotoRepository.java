package com.staymatch.repository;

import com.staymatch.entity.PropertyPhoto;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, Long> {

    List<PropertyPhoto> findAllByPropertyIdOrderByIdAsc(Long propertyId);

    Optional<PropertyPhoto> findByIdAndPropertyId(Long id, Long propertyId);

    long countByPropertyId(Long propertyId);

    List<PropertyPhoto> findAllByPropertyIdInOrderByPropertyIdAscIdAsc(Collection<Long> propertyIds);
}
