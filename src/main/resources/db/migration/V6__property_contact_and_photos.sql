-- Optional owner contact details and metadata for locally stored property photos.
-- Existing listings remain valid with a NULL phone and no photo rows.
ALTER TABLE properties
    ADD COLUMN contact_phone VARCHAR(32);

CREATE TABLE property_photos (
    id          BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    media_type  VARCHAR(20) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes  BIGINT NOT NULL CHECK (size_bytes > 0),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_property_photos_property_id ON property_photos(property_id);
