package com.staymatch.controller;

import com.staymatch.service.PropertyPhotoService;
import com.staymatch.service.PropertyPhotoService.StoredImage;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/photos")
public class PropertyPhotoController {

    private final PropertyPhotoService photoService;

    public PropertyPhotoController(PropertyPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> getPhoto(
            @PathVariable @Positive Long propertyId,
            @PathVariable @Positive Long photoId) {
        try {
            StoredImage image = photoService.getPublicPhoto(propertyId, photoId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(image.contentType()))
                    .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(image.bytes());
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.");
        }
    }
}
