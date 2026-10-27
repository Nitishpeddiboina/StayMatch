-- Development-only fictional sample data for local search development.
-- The seed owner's password marker is intentionally unusable as a login credential.
DO $$
BEGIN
    INSERT INTO users (name, email, password_hash, role)
    VALUES (
        'StayMatch Sample Owner',
        'sample-owner@staymatch.test',
        '!DISABLED_DEVELOPMENT_SEED_OWNER!',
        'OWNER'
    )
    ON CONFLICT (email) DO NOTHING;

    IF NOT EXISTS (
        SELECT 1
        FROM users
        WHERE email = 'sample-owner@staymatch.test'
          AND name = 'StayMatch Sample Owner'
          AND password_hash = '!DISABLED_DEVELOPMENT_SEED_OWNER!'
          AND role = 'OWNER'
    ) THEN
        RAISE EXCEPTION 'The sample owner email is already assigned to a different account';
    END IF;
END
$$;

INSERT INTO properties (
    owner_id, title, type, bhk, sharing_type, area, city, address_line,
    latitude, longitude, monthly_rent, deposit, gender_preference, is_active,
    description, created_at, updated_at
)
SELECT
    u.id,
    'Shared 3-Person PG near Gachibowli',
    'PG',
    NULL,
    3,
    'Gachibowli',
    'Hyderabad',
    'Fictional Sample Address 1, Gachibowli, Hyderabad',
    17.4401,
    78.3489,
    9500,
    9500,
    'ANY',
    TRUE,
    'Fictional sample PG listing for local search development.',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'sample-owner@staymatch.test'
  AND u.name = 'StayMatch Sample Owner'
  AND u.password_hash = '!DISABLED_DEVELOPMENT_SEED_OWNER!'
  AND u.role = 'OWNER'
  AND NOT EXISTS (
      SELECT 1 FROM properties p
      WHERE p.owner_id = u.id
        AND p.title = 'Shared 3-Person PG near Gachibowli'
        AND p.type = 'PG'
        AND p.area = 'Gachibowli'
        AND p.city = 'Hyderabad'
  );

INSERT INTO properties (
    owner_id, title, type, bhk, sharing_type, area, city, address_line,
    latitude, longitude, monthly_rent, deposit, gender_preference, is_active,
    description, created_at, updated_at
)
SELECT
    u.id,
    'Furnished Private Room in Kondapur',
    'ROOM',
    NULL,
    NULL,
    'Kondapur',
    'Hyderabad',
    'Fictional Sample Address 2, Kondapur, Hyderabad',
    17.4639,
    78.3568,
    14000,
    20000,
    'FEMALE',
    TRUE,
    'Fictional sample private room listing for local search development.',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'sample-owner@staymatch.test'
  AND u.name = 'StayMatch Sample Owner'
  AND u.password_hash = '!DISABLED_DEVELOPMENT_SEED_OWNER!'
  AND u.role = 'OWNER'
  AND NOT EXISTS (
      SELECT 1 FROM properties p
      WHERE p.owner_id = u.id
        AND p.title = 'Furnished Private Room in Kondapur'
        AND p.type = 'ROOM'
        AND p.area = 'Kondapur'
        AND p.city = 'Hyderabad'
  );

INSERT INTO properties (
    owner_id, title, type, bhk, sharing_type, area, city, address_line,
    latitude, longitude, monthly_rent, deposit, gender_preference, is_active,
    description, created_at, updated_at
)
SELECT
    u.id,
    '2 BHK Rental Home in Miyapur',
    'RENTAL_HOME',
    2,
    NULL,
    'Miyapur',
    'Hyderabad',
    'Fictional Sample Address 3, Miyapur, Hyderabad',
    17.4968,
    78.3570,
    24000,
    48000,
    'ANY',
    TRUE,
    'Fictional sample rental home listing for local search development.',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'sample-owner@staymatch.test'
  AND u.name = 'StayMatch Sample Owner'
  AND u.password_hash = '!DISABLED_DEVELOPMENT_SEED_OWNER!'
  AND u.role = 'OWNER'
  AND NOT EXISTS (
      SELECT 1 FROM properties p
      WHERE p.owner_id = u.id
        AND p.title = '2 BHK Rental Home in Miyapur'
        AND p.type = 'RENTAL_HOME'
        AND p.area = 'Miyapur'
        AND p.city = 'Hyderabad'
  );
