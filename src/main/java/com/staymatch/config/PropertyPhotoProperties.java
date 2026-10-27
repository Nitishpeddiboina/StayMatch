package com.staymatch.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "staymatch.photos")
public class PropertyPhotoProperties {

    private Path storageDirectory = Path.of("./staymatch-uploads/property-photos");
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    private int maxFilesPerProperty = 10;

    public Path getStorageDirectory() { return storageDirectory; }
    public void setStorageDirectory(Path storageDirectory) { this.storageDirectory = storageDirectory; }
    public DataSize getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getMaxFilesPerProperty() { return maxFilesPerProperty; }
    public void setMaxFilesPerProperty(int maxFilesPerProperty) { this.maxFilesPerProperty = maxFilesPerProperty; }
}
