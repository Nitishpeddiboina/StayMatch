package com.staymatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Metadata for an image stored outside the application source tree. */
@Entity
@Table(name = "property_photos")
public class PropertyPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    protected PropertyPhoto() {
        // Required by JPA.
    }

    public PropertyPhoto(Long propertyId, String storageKey, String mediaType, Long sizeBytes,
            LocalDateTime uploadedAt) {
        this.propertyId = propertyId;
        this.storageKey = storageKey;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPropertyId() { return propertyId; }
    public String getStorageKey() { return storageKey; }
    public String getMediaType() { return mediaType; }
    public Long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
