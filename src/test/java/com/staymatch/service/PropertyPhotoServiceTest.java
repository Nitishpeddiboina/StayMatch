package com.staymatch.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.staymatch.config.PropertyPhotoProperties;
import com.staymatch.dto.PropertyPhotoResponse;
import com.staymatch.entity.Property;
import com.staymatch.entity.PropertyPhoto;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.repository.PropertyPhotoRepository;
import com.staymatch.repository.PropertyRepository;
import com.staymatch.security.StayMatchUserDetails;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class PropertyPhotoServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyPhotoRepository photoRepository;

    private PropertyPhotoService service;
    private StayMatchUserDetails owner;
    private Property property;
    private PropertyPhotoProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PropertyPhotoProperties();
        properties.setStorageDirectory(temporaryDirectory.resolve("stored-photos"));
        properties.setMaxFileSize(DataSize.ofKilobytes(100));
        properties.setMaxFilesPerProperty(3);
        service = new PropertyPhotoService(propertyRepository, photoRepository, properties);
        User user = new User("Owner", "owner@example.test", "unused-hash", Role.OWNER);
        user.setId(42L);
        owner = new StayMatchUserDetails(user);
        property = Property.newListing();
        property.setId(8L);
        property.setOwnerId(42L);
        property.setActive(true);
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.of(property));
    }

    @Test
    void uploadDetectsPngFromBytesAndUsesGeneratedStorageName() throws Exception {
        when(photoRepository.countByPropertyId(8L)).thenReturn(0L);
        when(photoRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<PropertyPhoto> submitted = invocation.getArgument(0);
            List<PropertyPhoto> saved = new ArrayList<>();
            for (PropertyPhoto photo : submitted) {
                photo.setId(91L);
                saved.add(photo);
            }
            return saved;
        });
        byte[] contents = imageBytes("png");
        MockMultipartFile upload = new MockMultipartFile(
                "photos", "../../not-a-real-image.exe", "text/plain", contents);

        List<PropertyPhotoResponse> result = service.upload(owner, 8L, List.of(upload));

        assertEquals(1, result.size());
        assertEquals(91L, result.get(0).id());
        assertEquals("image/png", result.get(0).contentType());
        assertEquals(contents.length, result.get(0).sizeBytes());
        assertTrue(result.get(0).url().startsWith("/api/v1/properties/8/photos/91"));
        assertFalse(result.get(0).url().contains("not-a-real-image"));
        ArgumentCaptor<Iterable<PropertyPhoto>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(photoRepository).saveAll(captor.capture());
        PropertyPhoto metadata = captor.getValue().iterator().next();
        assertTrue(metadata.getStorageKey().matches("[0-9a-f-]{36}\\.png"));
        assertNotEquals("../../not-a-real-image.exe", metadata.getStorageKey());
        Path stored = properties.getStorageDirectory().resolve(metadata.getStorageKey());
        assertArrayEquals(contents, Files.readAllBytes(stored));
    }

    @Test
    void acceptsAValidJpegEvenWhenClientMimeTypeDisagrees() throws Exception {
        when(photoRepository.countByPropertyId(8L)).thenReturn(0L);
        when(photoRepository.saveAll(any())).thenAnswer(invocation -> {
            List<PropertyPhoto> submitted = new ArrayList<>();
            invocation.<Iterable<PropertyPhoto>>getArgument(0).forEach(submitted::add);
            submitted.forEach(photo -> photo.setId(92L));
            return submitted;
        });

        List<PropertyPhotoResponse> response = service.upload(owner, 8L,
                List.of(new MockMultipartFile("photos", "photo.png", "image/png", imageBytes("jpeg"))));

        assertEquals("image/jpeg", response.get(0).contentType());
    }

    @Test
    void acceptsWebpBasedOnItsRiffAndVp8Content() {
        when(photoRepository.countByPropertyId(8L)).thenReturn(0L);
        when(photoRepository.saveAll(any())).thenAnswer(invocation -> {
            List<PropertyPhoto> submitted = new ArrayList<>();
            invocation.<Iterable<PropertyPhoto>>getArgument(0).forEach(submitted::add);
            submitted.forEach(photo -> photo.setId(93L));
            return submitted;
        });
        byte[] webp = java.util.Arrays.copyOf(java.util.Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA=="), 42);

        List<PropertyPhotoResponse> response = service.upload(owner, 8L,
                List.of(new MockMultipartFile("photos", "one.webp", "application/octet-stream", webp)));

        assertEquals("image/webp", response.get(0).contentType());
    }

    @Test
    void rejectsNonImageContentAndDoesNotPersistMetadata() {
        when(photoRepository.countByPropertyId(8L)).thenReturn(0L);
        MockMultipartFile upload = new MockMultipartFile("photos", "photo.jpg", "image/jpeg", "not an image".getBytes());

        PropertyPhotoException exception = assertThrows(PropertyPhotoException.class,
                () -> service.upload(owner, 8L, List.of(upload)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(photoRepository, never()).saveAll(any());
    }

    @Test
    void rejectsFilesOverConfiguredSize() {
        properties.setMaxFileSize(DataSize.ofBytes(4));
        when(photoRepository.countByPropertyId(8L)).thenReturn(0L);

        PropertyPhotoException exception = assertThrows(PropertyPhotoException.class,
                () -> service.upload(owner, 8L, List.of(new MockMultipartFile(
                        "photos", "photo.png", "image/png", imageBytesUnchecked("png")))));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
        verify(photoRepository, never()).saveAll(any());
    }

    @Test
    void enforcesPerPropertyPhotoCount() {
        when(photoRepository.countByPropertyId(8L)).thenReturn(3L);

        PropertyPhotoException exception = assertThrows(PropertyPhotoException.class,
                () -> service.upload(owner, 8L, List.of(new MockMultipartFile(
                        "photos", "photo.png", "image/png", imageBytesUnchecked("png")))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(photoRepository, never()).saveAll(any());
    }

    @Test
    void anotherOwnerCannotListUploadOrDeletePhotos() {
        when(propertyRepository.findByIdAndOwnerId(8L, 42L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> service.listMine(owner, 8L));
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.upload(owner, 8L, List.of()));
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> service.delete(owner, 8L, 91L));
        verify(photoRepository, never()).delete(any(PropertyPhoto.class));
    }

    @Test
    void photoFromADifferentPropertyCannotBeDeletedThroughThisListing() {
        when(photoRepository.findByIdAndPropertyId(91L, 8L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.delete(owner, 8L, 91L));

        verify(photoRepository, never()).delete(any(PropertyPhoto.class));
    }

    @Test
    void ownerPhotoListReturnsMetadataWithoutStorageKey() {
        PropertyPhoto photo = new PropertyPhoto(8L,
                "01234567-89ab-cdef-0123-456789abcdef.png", "image/png", 120L,
                java.time.LocalDateTime.now());
        photo.setId(91L);
        when(photoRepository.findAllByPropertyIdOrderByIdAsc(8L)).thenReturn(List.of(photo));

        List<PropertyPhotoResponse> response = service.listMine(owner, 8L);

        assertEquals(1, response.size());
        assertEquals("/api/v1/properties/8/photos/91", response.get(0).url());
        assertEquals("image/png", response.get(0).contentType());
        assertFalse(response.get(0).url().contains("01234567"));
    }

    @Test
    void deleteRemovesOnlyPhotoOwnedByTheVerifiedProperty() throws Exception {
        Path directory = Files.createDirectories(properties.getStorageDirectory());
        Path stored = directory.resolve("01234567-89ab-cdef-0123-456789abcdef.png");
        Files.write(stored, imageBytes("png"));
        PropertyPhoto photo = new PropertyPhoto(8L, stored.getFileName().toString(), "image/png", 10L,
                java.time.LocalDateTime.now());
        photo.setId(91L);
        when(photoRepository.findByIdAndPropertyId(91L, 8L)).thenReturn(Optional.of(photo));

        service.delete(owner, 8L, 91L);

        verify(photoRepository).delete(photo);
        assertFalse(Files.exists(stored));
    }

    private byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        image.flush();
        return output.toByteArray();
    }

    private byte[] imageBytesUnchecked(String format) {
        try { return imageBytes(format); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
