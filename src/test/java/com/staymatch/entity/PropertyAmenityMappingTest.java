package com.staymatch.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PropertyAmenityMappingTest {

    @Test
    void mapsAmenityCatalogAndJoinTableColumns() throws NoSuchFieldException {
        assertEquals("amenities", Amenity.class.getAnnotation(Table.class).name());

        Field amenitiesField = Property.class.getDeclaredField("amenities");
        ManyToMany relationship = amenitiesField.getAnnotation(ManyToMany.class);
        JoinTable joinTable = amenitiesField.getAnnotation(JoinTable.class);

        assertEquals(FetchType.LAZY, relationship.fetch());
        assertEquals("property_amenities", joinTable.name());
        JoinColumn[] propertyColumns = joinTable.joinColumns();
        JoinColumn[] amenityColumns = joinTable.inverseJoinColumns();
        assertEquals("property_id", propertyColumns[0].name());
        assertEquals("amenity_id", amenityColumns[0].name());
    }

    @Test
    void amenitiesAreNotTraversedDuringExistingPropertyJsonSerialization() throws Exception {
        Property property = new Property();
        Amenity amenity = new Amenity();
        amenity.setId(1L);
        amenity.setName("WIFI");
        property.setAmenities(Set.of(amenity));

        String json = new ObjectMapper().writeValueAsString(property);

        assertFalse(json.contains("amenities"));
        assertFalse(json.contains("WIFI"));
    }
}
