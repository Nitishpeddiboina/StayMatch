package com.staymatch.controller;

import com.staymatch.dto.PropertyPhotoResponse;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.PropertyPhotoService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/owner/properties/{propertyId}/photos")
public class OwnerPropertyPhotoController {

    private final PropertyPhotoService photoService;

    public OwnerPropertyPhotoController(PropertyPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<PropertyPhotoResponse> list(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @PathVariable @Positive Long propertyId) {
        return photoService.listMine(owner, propertyId);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PropertyPhotoResponse> upload(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @PathVariable @Positive Long propertyId,
            @RequestPart("photos") List<MultipartFile> photos) {
        return photoService.upload(owner, propertyId, photos);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @PathVariable @Positive Long propertyId,
            @PathVariable @Positive Long photoId) {
        photoService.delete(owner, propertyId, photoId);
    }
}
