package com.staymatch.service;

import com.staymatch.config.PropertyPhotoProperties;
import com.staymatch.dto.PropertyPhotoResponse;
import com.staymatch.entity.Property;
import com.staymatch.entity.PropertyPhoto;
import com.staymatch.entity.Role;
import com.staymatch.repository.PropertyPhotoRepository;
import com.staymatch.repository.PropertyRepository;
import com.staymatch.security.StayMatchUserDetails;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PropertyPhotoService {

    private static final String NOT_FOUND = "Property or photo not found.";
    private static final long MAX_IMAGE_PIXELS = 8_000_000L;

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository photoRepository;
    private final PropertyPhotoProperties properties;

    public PropertyPhotoService(
            PropertyRepository propertyRepository,
            PropertyPhotoRepository photoRepository,
            PropertyPhotoProperties properties) {
        this.propertyRepository = propertyRepository;
        this.photoRepository = photoRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<PropertyPhotoResponse> listMine(StayMatchUserDetails owner, Long propertyId) {
        requireOwnedProperty(owner, propertyId);
        return photoRepository.findAllByPropertyIdOrderByIdAsc(propertyId).stream()
                .map(photo -> toResponse(propertyId, photo))
                .toList();
    }

    @Transactional
    public List<PropertyPhotoResponse> upload(
            StayMatchUserDetails owner, Long propertyId, List<MultipartFile> uploads) {
        requireOwnedProperty(owner, propertyId);
        if (uploads == null || uploads.isEmpty()) {
            throw invalid(HttpStatus.BAD_REQUEST, "Select at least one image to upload.");
        }
        int maxFiles = properties.getMaxFilesPerProperty();
        if (maxFiles < 1 || photoRepository.countByPropertyId(propertyId) + uploads.size() > maxFiles) {
            throw invalid(HttpStatus.BAD_REQUEST, "This listing has reached its photo limit.");
        }
        long maxBytes = properties.getMaxFileSize().toBytes();
        if (maxBytes < 1) {
            throw invalid(HttpStatus.INTERNAL_SERVER_ERROR, "Photo storage limits are not configured correctly.");
        }

        List<ValidatedImage> validated = uploads.stream().map(file -> validate(file, maxBytes)).toList();
        List<Path> writtenFiles = new ArrayList<>();
        List<PropertyPhoto> photos = new ArrayList<>();
        try {
            Path root = storageRoot();
            for (ValidatedImage image : validated) {
                String storageKey = UUID.randomUUID() + image.extension();
                Path target = resolveStorageKey(root, storageKey);
                Files.write(target, image.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                writtenFiles.add(target);
                photos.add(new PropertyPhoto(propertyId, storageKey, image.contentType(),
                        (long) image.bytes().length, LocalDateTime.now()));
            }
            return photoRepository.saveAll(photos).stream()
                    .map(photo -> toResponse(propertyId, photo))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            deleteFiles(writtenFiles);
            if (exception instanceof PropertyPhotoException photoException) {
                throw photoException;
            }
            throw invalid(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the selected property photos.");
        }
    }

    @Transactional
    public void delete(StayMatchUserDetails owner, Long propertyId, Long photoId) {
        requireOwnedProperty(owner, propertyId);
        PropertyPhoto photo = photoRepository.findByIdAndPropertyId(photoId, propertyId)
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        photoRepository.delete(photo);
        Path path = safePath(photo.getStorageKey());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFiles(List.of(path));
                }
            });
        } else {
            deleteFiles(List.of(path));
        }
    }

    @Transactional(readOnly = true)
    public StoredImage getPublicPhoto(Long propertyId, Long photoId) {
        // Match the existing public GET /properties/{id} behavior; search still filters inactive listings.
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        PropertyPhoto photo = photoRepository.findByIdAndPropertyId(photoId, property.getId())
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        try {
            Path path = safePath(photo.getStorageKey());
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new EntityNotFoundException(NOT_FOUND);
            }
            return new StoredImage(Files.readAllBytes(path), photo.getMediaType());
        } catch (IOException exception) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
    }

    private ValidatedImage validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
            throw invalid(HttpStatus.PAYLOAD_TOO_LARGE, "Each photo must be non-empty and within the configured size limit.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > maxBytes) {
                throw invalid(HttpStatus.PAYLOAD_TOO_LARGE, "Each photo must be non-empty and within the configured size limit.");
            }
            if (matches(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
                validateDecodedImage(bytes, "image/jpeg");
                return new ValidatedImage(bytes, "image/jpeg", ".jpg");
            }
            if (matches(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
                validateDecodedImage(bytes, "image/png");
                return new ValidatedImage(bytes, "image/png", ".png");
            }
            if (isWebp(bytes)) {
                return new ValidatedImage(bytes, "image/webp", ".webp");
            }
            throw invalid(HttpStatus.BAD_REQUEST, "Only valid JPEG, PNG, or WebP images are accepted.");
        } catch (IOException exception) {
            throw invalid(HttpStatus.BAD_REQUEST, "The selected file is not a valid supported image.");
        }
    }

    private void validateDecodedImage(byte[] bytes, String expectedFormat) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("Image data could not be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Image decoder is unavailable.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!expectedFormat.substring("image/".length()).equals(format)
                        && !(expectedFormat.equals("image/jpeg") && format.equals("jpg"))) {
                    throw new IOException("Image format does not match its content signature.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Image dimensions are outside the supported limit.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new IOException("Image data could not be decoded.");
                }
                decoded.flush();
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean isWebp(byte[] bytes) {
        if (bytes.length < 30 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")) {
            return false;
        }
        long riffSize = littleEndian32(bytes, 4);
        long chunkSize = littleEndian32(bytes, 16);
        if (riffSize + 8 != bytes.length || chunkSize + 20 > bytes.length) {
            return false;
        }
        String chunk = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int width;
        int height;
        if (chunk.equals("VP8X") && chunkSize >= 10) {
            width = 1 + littleEndian24(bytes, 24);
            height = 1 + littleEndian24(bytes, 27);
        } else if (chunk.equals("VP8L") && chunkSize >= 5 && bytes[20] == 0x2f) {
            int b1 = bytes[21] & 0xff;
            int b2 = bytes[22] & 0xff;
            int b3 = bytes[23] & 0xff;
            int b4 = bytes[24] & 0xff;
            width = 1 + b1 + ((b2 & 0x3f) << 8);
            height = 1 + ((b2 >> 6) | (b3 << 2) | ((b4 & 0x0f) << 10));
        } else if (chunk.equals("VP8 ") && chunkSize >= 10 && bytes[23] == (byte) 0x9d
                && bytes[24] == 0x01 && bytes[25] == 0x2a) {
            width = ((bytes[27] & 0x3f) << 8) | (bytes[26] & 0xff);
            height = ((bytes[29] & 0x3f) << 8) | (bytes[28] & 0xff);
        } else {
            return false;
        }
        return width > 0 && height > 0 && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        return bytes.length >= offset + value.length()
                && value.equals(new String(bytes, offset, value.length(), java.nio.charset.StandardCharsets.US_ASCII));
    }

    private long littleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private boolean matches(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) return false;
        }
        return true;
    }

    private Path storageRoot() throws IOException {
        Path configured = properties.getStorageDirectory().toAbsolutePath().normalize();
        Files.createDirectories(configured);
        return configured.toRealPath();
    }

    private Path safePath(String storageKey) {
        if (storageKey == null || !storageKey.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)")) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
        try {
            Path root = storageRoot();
            Path candidate = root.resolve(storageKey).normalize();
            if (!candidate.startsWith(root)) {
                throw new EntityNotFoundException(NOT_FOUND);
            }
            return candidate;
        } catch (IOException exception) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
    }

    private Path resolveStorageKey(Path root, String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw invalid(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the selected property photos.");
        }
        return target;
    }

    private void deleteFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // A missing file does not expose its location or affect listing ownership.
            }
        }
    }

    private Property requireOwnedProperty(StayMatchUserDetails owner, Long propertyId) {
        if (owner == null || owner.getRole() != Role.OWNER) {
            throw new org.springframework.security.access.AccessDeniedException("Owner role is required.");
        }
        return propertyRepository.findByIdAndOwnerId(propertyId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    private PropertyPhotoResponse toResponse(Long propertyId, PropertyPhoto photo) {
        return new PropertyPhotoResponse(photo.getId(),
                "/api/v1/properties/" + propertyId + "/photos/" + photo.getId(),
                photo.getMediaType(), photo.getSizeBytes(), photo.getUploadedAt());
    }

    private PropertyPhotoException invalid(HttpStatus status, String message) {
        return new PropertyPhotoException(status, message);
    }

    private record ValidatedImage(byte[] bytes, String contentType, String extension) { }

    public record StoredImage(byte[] bytes, String contentType) { }
}
