-- ============================================================
-- V2__staymatch_schema.sql
--
-- Creates the full StayMatch V1 database schema.
-- Managed by Flyway. Do NOT run this manually.
--
-- Table creation order matters because of foreign keys:
--   1. users          (referenced by properties, saved_properties)
--   2. amenities      (referenced by property_amenities)
--   3. properties     (references users; referenced by property_amenities, saved_properties)
--   4. property_amenities  (references properties + amenities)
--   5. saved_properties    (references users + properties)
-- ============================================================


-- ============================================================
-- TABLE: users
--
-- Stores all registered users, both SEEKERs and OWNERs.
--
-- Columns:
--   id            : auto-incrementing surrogate primary key
--   name          : display name of the user
--   email         : login identifier, must be unique across all users
--   password_hash : BCrypt-hashed password (plain text never stored)
--   role          : either 'SEEKER' (searching for a property) or
--                   'OWNER' (posting properties for rent)
--   created_at    : timestamp when the account was created
--
-- Constraints:
--   CHECK on role  : only 'SEEKER' or 'OWNER' are valid values
-- ============================================================
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'SEEKER'
                               CHECK (role IN ('SEEKER', 'OWNER')),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);


-- ============================================================
-- TABLE: amenities
--
-- A reference/lookup table of all amenity types available
-- in the system (e.g., WIFI, AC, PARKING).
--
-- Columns:
--   id   : surrogate primary key
--   name : short, uppercase identifier used in search params
--          and API requests. Must be globally unique.
--
-- Note: This table is populated once via seed data (see below).
-- New amenities are added here by a developer, not by users.
-- ============================================================
CREATE TABLE amenities (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);


-- ============================================================
-- TABLE: properties
--
-- The central table. Stores PGs, rooms, and rental homes
-- posted by OWNER users.
--
-- Columns:
--   id                : surrogate primary key
--   owner_id          : FK to users. The OWNER who posted this.
--   title             : short headline for the listing
--   type              : one of PG, ROOM, RENTAL_HOME
--   bhk               : number of bedrooms (1/2/3). Only used
--                       when type = RENTAL_HOME. NULL otherwise.
--   sharing_type      : number of people sharing a room (1/2/3).
--                       Only used when type = PG. NULL otherwise.
--   area              : locality name (e.g., "Gachibowli")
--   city              : city name (e.g., "Hyderabad")
--   address_line      : full street address
--   latitude          : geographic coordinate for distance calculation.
--                       Populated manually in test data;
--                       geocoding API will populate it later.
--   longitude         : geographic coordinate (paired with latitude)
--   monthly_rent      : rent in Indian Rupees (₹), must be positive
--   deposit           : security deposit in ₹, can be zero
--   gender_preference : who the property is open to
--   is_active         : soft-delete flag. FALSE = hidden from search.
--   description       : free-text description by the owner
--   created_at        : when the listing was first posted
--   updated_at        : when the listing was last modified
--
-- Constraints:
--   CHECK on type             : only PG, ROOM, RENTAL_HOME
--   CHECK on bhk              : only 1, 2, 3 (or NULL)
--   CHECK on sharing_type     : only 1, 2, 3 (or NULL)
--   CHECK on monthly_rent     : must be greater than 0
--   CHECK on deposit          : must be 0 or more
--   CHECK on gender_preference: only MALE, FEMALE, ANY
-- ============================================================
CREATE TABLE properties (
    id                BIGSERIAL    PRIMARY KEY,
    owner_id          BIGINT       NOT NULL
                                   REFERENCES users(id) ON DELETE RESTRICT,
    title             VARCHAR(200) NOT NULL,
    type              VARCHAR(20)  NOT NULL
                                   CHECK (type IN ('PG', 'ROOM', 'RENTAL_HOME')),
    bhk               INTEGER      CHECK (bhk IN (1, 2, 3)),
    sharing_type      INTEGER      CHECK (sharing_type IN (1, 2, 3)),
    area              VARCHAR(100) NOT NULL,
    city              VARCHAR(100) NOT NULL,
    address_line      TEXT         NOT NULL,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    monthly_rent      INTEGER      NOT NULL CHECK (monthly_rent > 0),
    deposit           INTEGER      NOT NULL DEFAULT 0
                                   CHECK (deposit >= 0),
    gender_preference VARCHAR(10)  NOT NULL DEFAULT 'ANY'
                                   CHECK (gender_preference IN ('MALE', 'FEMALE', 'ANY')),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    description       TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Index: speed up the most common search query — filter by area + type
CREATE INDEX idx_properties_area       ON properties(area);
CREATE INDEX idx_properties_type       ON properties(type);
CREATE INDEX idx_properties_is_active  ON properties(is_active);
CREATE INDEX idx_properties_owner_id   ON properties(owner_id);


-- ============================================================
-- TABLE: property_amenities
--
-- Join table implementing the many-to-many relationship between
-- properties and amenities.
--
-- Example:
--   Property 1 (Gachibowli PG) has amenities: WIFI, AC, PARKING
--   Property 2 (Hitech City Room) has amenities: WIFI, HOT_WATER
--
-- A composite primary key on (property_id, amenity_id) ensures:
--   - no duplicate amenity entries for the same property
--   - no surrogate PK needed (the pair is naturally unique)
--
-- ON DELETE CASCADE: if a property is fully deleted, its amenity
-- links are automatically removed too. Same for amenity deletion.
-- ============================================================
CREATE TABLE property_amenities (
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    amenity_id  BIGINT NOT NULL REFERENCES amenities(id)  ON DELETE CASCADE,
    PRIMARY KEY (property_id, amenity_id)
);


-- ============================================================
-- TABLE: saved_properties
--
-- Allows SEEKER users to bookmark/shortlist properties they
-- are interested in.
--
-- This is a many-to-many between users and properties:
--   - one user can save many properties
--   - one property can be saved by many users
--
-- Composite PK (user_id, property_id) ensures a user cannot
-- save the same property more than once.
--
-- saved_at: records when the user added it to their shortlist.
-- ON DELETE CASCADE: if a user or property is deleted, their
-- saved links are automatically cleaned up.
-- ============================================================
CREATE TABLE saved_properties (
    user_id     BIGINT    NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    property_id BIGINT    NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    saved_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, property_id)
);


-- ============================================================
-- SEED DATA: amenities
--
-- These are the initial amenity options available in the system.
-- Names are uppercase constants — consistent with enum-style
-- identifiers used in Java and REST API query parameters.
--
-- Display labels (e.g., "Wi-Fi" for WIFI) will be handled
-- by the React frontend.
-- ============================================================
INSERT INTO amenities (name) VALUES
    ('WIFI'),
    ('AC'),
    ('TV'),
    ('HOT_WATER'),
    ('WASHING_MACHINE'),
    ('PARKING'),
    ('FOOD'),
    ('GYM'),
    ('GEYSER'),
    ('CCTV'),
    ('POWER_BACKUP');
