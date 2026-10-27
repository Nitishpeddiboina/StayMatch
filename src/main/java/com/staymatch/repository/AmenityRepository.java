package com.staymatch.repository;

import com.staymatch.entity.Amenity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    List<Amenity> findByNameIn(Collection<String> names);
}
