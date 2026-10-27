package com.staymatch.repository;

import com.staymatch.entity.Property;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PropertyRepository extends JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {

    List<Property> findAllByOwnerIdOrderByIdDesc(Long ownerId);

    Optional<Property> findByIdAndOwnerId(Long id, Long ownerId);
}
