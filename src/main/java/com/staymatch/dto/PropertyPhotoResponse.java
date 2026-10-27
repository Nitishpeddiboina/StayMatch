package com.staymatch.dto;

import java.time.LocalDateTime;

/** Safe photo metadata; storage keys and filesystem paths are not exposed. */
public record PropertyPhotoResponse(
        Long id,
        String url,
        String contentType,
        Long sizeBytes,
        LocalDateTime uploadedAt) {
}
