-- Add 60 fictional Hyderabad listings for local search and matching development.
-- Stable listing key: owner email + (title, type, area, city). The key uses
-- existing columns because the current schema has no seed-key column. Re-running
-- this statement skips matching property rows and re-adds any missing amenity links.
-- No numeric owner or amenity IDs are assumed; all references are resolved by email/name.

DO $$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM users
        WHERE email = 'sample-owner@staymatch.test'
          AND name = 'StayMatch Sample Owner'
          AND role = 'OWNER'
    ) <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one StayMatch sample owner (sample-owner@staymatch.test)';
    END IF;
END
$$;

WITH owner AS (
    SELECT id
    FROM users
    WHERE email = 'sample-owner@staymatch.test'
      AND name = 'StayMatch Sample Owner'
      AND role = 'OWNER'
),
seed (
    title, type, bhk, sharing_type, area, address_line,
    monthly_rent, deposit, gender_preference, description, amenity_names
) AS (
    VALUES
        ('Sunlit Shared PG near Botanical Garden', 'PG', NULL, 3, 'Gachibowli', 'Fictional Lane 01, Gachibowli, Hyderabad', 8500, 8500, 'ANY', 'Fictional three-sharing PG with meals and a bright common room.', ARRAY['WIFI','FOOD','CCTV','POWER_BACKUP']),
        ('Quiet Double Sharing Stay in Kondapur', 'PG', NULL, 2, 'Kondapur', 'Fictional Lane 02, Kondapur, Hyderabad', 10500, 15000, 'FEMALE', 'Fictional double-sharing stay with a staffed entrance and daily housekeeping.', ARRAY['WIFI','FOOD','AC','CCTV']),
        ('Budget Triple Sharing Home in Miyapur', 'PG', NULL, 3, 'Miyapur', 'Fictional Lane 03, Miyapur, Hyderabad', 7200, 7200, 'MALE', 'Fictional value stay with shared meals and easy metro access.', ARRAY['WIFI','FOOD','HOT_WATER']),
        ('Comfortable Twin PG near Kukatpally Metro', 'PG', NULL, 2, 'Kukatpally', 'Fictional Lane 04, Kukatpally, Hyderabad', 9000, 18000, 'ANY', 'Fictional twin-sharing rooms with a common lounge and power backup.', ARRAY['WIFI','TV','POWER_BACKUP','PARKING']),
        ('Private Sharing PG in Madhapur', 'PG', NULL, 1, 'Madhapur', 'Fictional Lane 05, Madhapur, Hyderabad', 16500, 20000, 'FEMALE', 'Fictional single-occupancy PG room with meals and air conditioning.', ARRAY['WIFI','FOOD','AC','GEYSER','CCTV']),
        ('Friendly Three Sharing in Manikonda', 'PG', NULL, 3, 'Manikonda', 'Fictional Lane 06, Manikonda, Hyderabad', 7800, 10000, 'MALE', 'Fictional shared accommodation with laundry access and a quiet study area.', ARRAY['WIFI','WASHING_MACHINE','HOT_WATER','PARKING']),
        ('Modern Twin Stay at Nallagandla', 'PG', NULL, 2, 'Nallagandla', 'Fictional Lane 07, Nallagandla, Hyderabad', 9800, 12000, 'ANY', 'Fictional two-person rooms near shops and bus connections.', ARRAY['WIFI','FOOD','GYM','POWER_BACKUP']),
        ('Affordable Shared Rooms in Ameerpet', 'PG', NULL, 3, 'Ameerpet', 'Fictional Lane 08, Ameerpet, Hyderabad', 6800, 6800, 'ANY', 'Fictional triple-sharing rooms with hot water and a common kitchen.', ARRAY['WIFI','HOT_WATER','GEYSER','CCTV']),
        ('Airy Twin PG near Hitec City', 'PG', NULL, 2, 'Hitec City', 'Fictional Lane 09, Hitec City, Hyderabad', 12500, 25000, 'FEMALE', 'Fictional twin rooms with air conditioning and secure access.', ARRAY['WIFI','AC','CCTV','POWER_BACKUP']),
        ('Simple Triple Stay in Begumpet', 'PG', NULL, 3, 'Begumpet', 'Fictional Lane 10, Begumpet, Hyderabad', 7500, 7500, 'MALE', 'Fictional shared stay with optional meal service and parking.', ARRAY['WIFI','FOOD','PARKING']),
        ('Premium Single PG in Jubilee Hills', 'PG', NULL, 1, 'Jubilee Hills', 'Fictional Lane 11, Jubilee Hills, Hyderabad', 19000, 30000, 'ANY', 'Fictional single room with gym access and dependable power backup.', ARRAY['WIFI','AC','GYM','POWER_BACKUP','CCTV']),
        ('Value Twin Sharing in Chandanagar', 'PG', NULL, 2, 'Chandanagar', 'Fictional Lane 12, Chandanagar, Hyderabad', 8200, 10000, 'ANY', 'Fictional twin-sharing home with laundry and hot water.', ARRAY['WIFI','WASHING_MACHINE','HOT_WATER']),
        ('Three Sharing PG near Uppal Metro', 'PG', NULL, 3, 'Uppal', 'Fictional Lane 13, Uppal, Hyderabad', 7000, 7000, 'MALE', 'Fictional budget rooms with meals and a monitored entrance.', ARRAY['WIFI','FOOD','CCTV']),
        ('Calm Double Sharing in Secunderabad', 'PG', NULL, 2, 'Secunderabad', 'Fictional Lane 14, Secunderabad, Hyderabad', 9300, 14000, 'FEMALE', 'Fictional double rooms with television and backup power.', ARRAY['WIFI','TV','POWER_BACKUP','GEYSER']),
        ('Single Occupancy PG at Financial District', 'PG', NULL, 1, 'Financial District', 'Fictional Lane 15, Financial District, Hyderabad', 17500, 25000, 'ANY', 'Fictional private PG room with gym access and housekeeping.', ARRAY['WIFI','GYM','AC','WASHING_MACHINE']),
        ('Easygoing Triple Stay in Attapur', 'PG', NULL, 3, 'Attapur', 'Fictional Lane 16, Attapur, Hyderabad', 6900, 9000, 'ANY', 'Fictional shared accommodation with parking and hot water.', ARRAY['WIFI','PARKING','HOT_WATER']),
        ('Twin Sharing near Dilsukhnagar', 'PG', NULL, 2, 'Dilsukhnagar', 'Fictional Lane 17, Dilsukhnagar, Hyderabad', 8000, 12000, 'MALE', 'Fictional twin rooms with meal options and laundry access.', ARRAY['WIFI','FOOD','WASHING_MACHINE']),
        ('Garden View Single PG in Kompally', 'PG', NULL, 1, 'Kompally', 'Fictional Lane 18, Kompally, Hyderabad', 13500, 20000, 'FEMALE', 'Fictional single room with air conditioning and secure entry.', ARRAY['WIFI','AC','CCTV','GEYSER']),
        ('Three Sharing Home in Bachupally', 'PG', NULL, 3, 'Bachupally', 'Fictional Lane 19, Bachupally, Hyderabad', 7400, 7400, 'ANY', 'Fictional shared home with meals and a common television room.', ARRAY['WIFI','FOOD','TV']),
        ('Bright Twin PG in Nanakramguda', 'PG', NULL, 2, 'Nanakramguda', 'Fictional Lane 20, Nanakramguda, Hyderabad', 11500, 18000, 'ANY', 'Fictional twin-sharing rooms with backup power and gym access.', ARRAY['WIFI','GYM','POWER_BACKUP','PARKING']),

        ('Furnished Private Room near Gachibowli Market', 'ROOM', NULL, NULL, 'Gachibowli', 'Fictional Lane 21, Gachibowli, Hyderabad', 14500, 25000, 'ANY', 'Fictional furnished private room with wardrobe and attached washroom.', ARRAY['WIFI','AC','GEYSER','CCTV']),
        ('Sunny Room in a Kondapur Apartment', 'ROOM', NULL, NULL, 'Kondapur', 'Fictional Lane 22, Kondapur, Hyderabad', 12000, 24000, 'FEMALE', 'Fictional private bedroom in a shared apartment with kitchen access.', ARRAY['WIFI','WASHING_MACHINE','PARKING']),
        ('Compact Furnished Room at Miyapur', 'ROOM', NULL, NULL, 'Miyapur', 'Fictional Lane 23, Miyapur, Hyderabad', 9500, 15000, 'MALE', 'Fictional compact room near transit with hot water and a desk.', ARRAY['WIFI','HOT_WATER','TV']),
        ('Private Room beside Kukatpally Park', 'ROOM', NULL, NULL, 'Kukatpally', 'Fictional Lane 24, Kukatpally, Hyderabad', 11000, 20000, 'ANY', 'Fictional room with balcony access and a shared modern kitchen.', ARRAY['WIFI','AC','POWER_BACKUP']),
        ('Work Ready Room in Madhapur', 'ROOM', NULL, NULL, 'Madhapur', 'Fictional Lane 25, Madhapur, Hyderabad', 16000, 30000, 'ANY', 'Fictional furnished room with a work desk and reliable backup power.', ARRAY['WIFI','AC','GEYSER','POWER_BACKUP']),
        ('Quiet Private Room in Manikonda', 'ROOM', NULL, NULL, 'Manikonda', 'Fictional Lane 26, Manikonda, Hyderabad', 10500, 18000, 'FEMALE', 'Fictional private room in a calm apartment with laundry access.', ARRAY['WIFI','WASHING_MACHINE','CCTV']),
        ('Bright Room at Nallagandla', 'ROOM', NULL, NULL, 'Nallagandla', 'Fictional Lane 27, Nallagandla, Hyderabad', 12500, 25000, 'ANY', 'Fictional room with air conditioning and nearby grocery options.', ARRAY['WIFI','AC','PARKING']),
        ('Simple Private Room in Ameerpet', 'ROOM', NULL, NULL, 'Ameerpet', 'Fictional Lane 28, Ameerpet, Hyderabad', 8500, 12000, 'MALE', 'Fictional room with shared kitchen, geyser, and television.', ARRAY['WIFI','GEYSER','TV']),
        ('Furnished Room near Hitec City Offices', 'ROOM', NULL, NULL, 'Hitec City', 'Fictional Lane 29, Hitec City, Hyderabad', 17000, 34000, 'FEMALE', 'Fictional private room with air conditioning and monitored entry.', ARRAY['WIFI','AC','CCTV','POWER_BACKUP']),
        ('Comfortable Room in Begumpet', 'ROOM', NULL, NULL, 'Begumpet', 'Fictional Lane 30, Begumpet, Hyderabad', 10000, 20000, 'ANY', 'Fictional room with a washing machine and secure parking.', ARRAY['WIFI','WASHING_MACHINE','PARKING']),
        ('Garden Facing Room in Jubilee Hills', 'ROOM', NULL, NULL, 'Jubilee Hills', 'Fictional Lane 31, Jubilee Hills, Hyderabad', 21000, 40000, 'ANY', 'Fictional spacious room with gym access and attached geyser.', ARRAY['WIFI','GYM','AC','GEYSER']),
        ('Private Room near Chandanagar Station', 'ROOM', NULL, NULL, 'Chandanagar', 'Fictional Lane 32, Chandanagar, Hyderabad', 9000, 16000, 'FEMALE', 'Fictional tidy room with kitchen use and hot water.', ARRAY['WIFI','HOT_WATER','WASHING_MACHINE']),
        ('Airy Room in Uppal', 'ROOM', NULL, NULL, 'Uppal', 'Fictional Lane 33, Uppal, Hyderabad', 9500, 15000, 'MALE', 'Fictional private room with parking and a shared television.', ARRAY['WIFI','PARKING','TV']),
        ('Quiet Furnished Room in Secunderabad', 'ROOM', NULL, NULL, 'Secunderabad', 'Fictional Lane 34, Secunderabad, Hyderabad', 11500, 23000, 'ANY', 'Fictional room with air conditioning and backup power.', ARRAY['WIFI','AC','POWER_BACKUP','CCTV']),
        ('Modern Room at Financial District', 'ROOM', NULL, NULL, 'Financial District', 'Fictional Lane 35, Financial District, Hyderabad', 18500, 35000, 'ANY', 'Fictional furnished room with gym access and laundry facilities.', ARRAY['WIFI','GYM','WASHING_MACHINE','AC']),
        ('Budget Room in Attapur', 'ROOM', NULL, NULL, 'Attapur', 'Fictional Lane 36, Attapur, Hyderabad', 8200, 12000, 'MALE', 'Fictional private room with geyser and convenient parking.', ARRAY['WIFI','GEYSER','PARKING']),
        ('Ready to Move Room at Dilsukhnagar', 'ROOM', NULL, NULL, 'Dilsukhnagar', 'Fictional Lane 37, Dilsukhnagar, Hyderabad', 8800, 15000, 'FEMALE', 'Fictional room with a washing machine and secure common areas.', ARRAY['WIFI','WASHING_MACHINE','CCTV']),
        ('Comfort Room in Kompally', 'ROOM', NULL, NULL, 'Kompally', 'Fictional Lane 38, Kompally, Hyderabad', 10800, 20000, 'ANY', 'Fictional private room with television and hot water.', ARRAY['WIFI','TV','HOT_WATER']),
        ('Work Friendly Room in Bachupally', 'ROOM', NULL, NULL, 'Bachupally', 'Fictional Lane 39, Bachupally, Hyderabad', 9800, 18000, 'ANY', 'Fictional furnished room with power backup and laundry access.', ARRAY['WIFI','POWER_BACKUP','WASHING_MACHINE']),
        ('Premium Private Room in Nanakramguda', 'ROOM', NULL, NULL, 'Nanakramguda', 'Fictional Lane 40, Nanakramguda, Hyderabad', 17500, 35000, 'FEMALE', 'Fictional private room with air conditioning, gym, and monitored entry.', ARRAY['WIFI','AC','GYM','CCTV']),

        ('One BHK Home near Gachibowli Stadium', 'RENTAL_HOME', 1, NULL, 'Gachibowli', 'Fictional Lane 41, Gachibowli, Hyderabad', 26000, 52000, 'ANY', 'Fictional one-bedroom home with balcony and covered parking.', ARRAY['WIFI','AC','PARKING','POWER_BACKUP']),
        ('Two BHK Apartment in Kondapur', 'RENTAL_HOME', 2, NULL, 'Kondapur', 'Fictional Lane 42, Kondapur, Hyderabad', 32000, 64000, 'ANY', 'Fictional two-bedroom apartment with lift and a shared gym.', ARRAY['WIFI','GYM','PARKING','CCTV']),
        ('Family Three BHK Home at Miyapur', 'RENTAL_HOME', 3, NULL, 'Miyapur', 'Fictional Lane 43, Miyapur, Hyderabad', 36000, 72000, 'ANY', 'Fictional three-bedroom home with power backup and laundry space.', ARRAY['WIFI','WASHING_MACHINE','POWER_BACKUP','TV']),
        ('Compact One BHK in Kukatpally', 'RENTAL_HOME', 1, NULL, 'Kukatpally', 'Fictional Lane 44, Kukatpally, Hyderabad', 19000, 38000, 'ANY', 'Fictional one-bedroom apartment near shops with secure parking.', ARRAY['WIFI','PARKING','CCTV']),
        ('Bright Two BHK in Madhapur', 'RENTAL_HOME', 2, NULL, 'Madhapur', 'Fictional Lane 45, Madhapur, Hyderabad', 39000, 78000, 'ANY', 'Fictional two-bedroom apartment with air conditioning and backup power.', ARRAY['WIFI','AC','POWER_BACKUP','GEYSER']),
        ('Three BHK Family Apartment in Manikonda', 'RENTAL_HOME', 3, NULL, 'Manikonda', 'Fictional Lane 46, Manikonda, Hyderabad', 34000, 68000, 'ANY', 'Fictional family home with gym access, parking, and laundry facilities.', ARRAY['WIFI','GYM','PARKING','WASHING_MACHINE']),
        ('One BHK Starter Home at Nallagandla', 'RENTAL_HOME', 1, NULL, 'Nallagandla', 'Fictional Lane 47, Nallagandla, Hyderabad', 21000, 42000, 'ANY', 'Fictional one-bedroom home with a practical kitchen and geyser.', ARRAY['WIFI','GEYSER','PARKING']),
        ('Two BHK near Ameerpet Metro', 'RENTAL_HOME', 2, NULL, 'Ameerpet', 'Fictional Lane 48, Ameerpet, Hyderabad', 27000, 54000, 'ANY', 'Fictional two-bedroom apartment with television and lift backup.', ARRAY['WIFI','TV','POWER_BACKUP']),
        ('Premium Three BHK in Hitec City', 'RENTAL_HOME', 3, NULL, 'Hitec City', 'Fictional Lane 49, Hitec City, Hyderabad', 52000, 104000, 'ANY', 'Fictional spacious apartment with gym, parking, and monitored entry.', ARRAY['WIFI','GYM','PARKING','CCTV','AC']),
        ('One BHK Home in Begumpet', 'RENTAL_HOME', 1, NULL, 'Begumpet', 'Fictional Lane 50, Begumpet, Hyderabad', 22000, 44000, 'ANY', 'Fictional one-bedroom home with air conditioning and hot water.', ARRAY['WIFI','AC','HOT_WATER']),
        ('Two BHK Garden Apartment in Jubilee Hills', 'RENTAL_HOME', 2, NULL, 'Jubilee Hills', 'Fictional Lane 51, Jubilee Hills, Hyderabad', 48000, 96000, 'ANY', 'Fictional two-bedroom apartment with gym and washing machine.', ARRAY['WIFI','GYM','WASHING_MACHINE','POWER_BACKUP']),
        ('Three BHK in Chandanagar', 'RENTAL_HOME', 3, NULL, 'Chandanagar', 'Fictional Lane 52, Chandanagar, Hyderabad', 30000, 60000, 'ANY', 'Fictional family apartment with parking and reliable power backup.', ARRAY['WIFI','PARKING','POWER_BACKUP','TV']),
        ('One BHK Flat at Uppal', 'RENTAL_HOME', 1, NULL, 'Uppal', 'Fictional Lane 53, Uppal, Hyderabad', 17500, 35000, 'ANY', 'Fictional one-bedroom flat with geyser and monitored entrance.', ARRAY['WIFI','GEYSER','CCTV']),
        ('Two BHK near Secunderabad Railway Station', 'RENTAL_HOME', 2, NULL, 'Secunderabad', 'Fictional Lane 54, Secunderabad, Hyderabad', 28000, 56000, 'ANY', 'Fictional two-bedroom home with washing machine and parking.', ARRAY['WIFI','WASHING_MACHINE','PARKING','HOT_WATER']),
        ('Three BHK Work District Residence', 'RENTAL_HOME', 3, NULL, 'Financial District', 'Fictional Lane 55, Financial District, Hyderabad', 56000, 112000, 'ANY', 'Fictional three-bedroom residence with gym, AC, and backup power.', ARRAY['WIFI','GYM','AC','POWER_BACKUP','CCTV']),
        ('One BHK Home in Attapur', 'RENTAL_HOME', 1, NULL, 'Attapur', 'Fictional Lane 56, Attapur, Hyderabad', 18000, 36000, 'ANY', 'Fictional one-bedroom home with parking and a shared television.', ARRAY['WIFI','PARKING','TV']),
        ('Two BHK near Dilsukhnagar Market', 'RENTAL_HOME', 2, NULL, 'Dilsukhnagar', 'Fictional Lane 57, Dilsukhnagar, Hyderabad', 24500, 49000, 'ANY', 'Fictional apartment with laundry facilities and geysers.', ARRAY['WIFI','WASHING_MACHINE','GEYSER']),
        ('Three BHK Family Home in Kompally', 'RENTAL_HOME', 3, NULL, 'Kompally', 'Fictional Lane 58, Kompally, Hyderabad', 31500, 63000, 'ANY', 'Fictional family home with a gym, parking, and secure entry.', ARRAY['WIFI','GYM','PARKING','CCTV']),
        ('One BHK Apartment in Bachupally', 'RENTAL_HOME', 1, NULL, 'Bachupally', 'Fictional Lane 59, Bachupally, Hyderabad', 16500, 33000, 'ANY', 'Fictional one-bedroom apartment with hot water and power backup.', ARRAY['WIFI','HOT_WATER','POWER_BACKUP']),
        ('Two BHK Modern Home in Nanakramguda', 'RENTAL_HOME', 2, NULL, 'Nanakramguda', 'Fictional Lane 60, Nanakramguda, Hyderabad', 42000, 84000, 'ANY', 'Fictional two-bedroom home with AC, laundry facilities, and parking.', ARRAY['WIFI','AC','WASHING_MACHINE','PARKING'])
),
inserted AS (
    INSERT INTO properties (
        owner_id, title, type, bhk, sharing_type, area, city, address_line,
        monthly_rent, deposit, gender_preference, is_active, description,
        created_at, updated_at
    )
    SELECT
        o.id, s.title, s.type, s.bhk, s.sharing_type, s.area, 'Hyderabad',
        s.address_line, s.monthly_rent, s.deposit, s.gender_preference, TRUE,
        s.description, NOW(), NOW()
    FROM seed s
    CROSS JOIN owner o
    WHERE NOT EXISTS (
        SELECT 1
        FROM properties p
        WHERE p.owner_id = o.id
          AND p.title = s.title
          AND p.type = s.type
          AND p.area = s.area
          AND p.city = 'Hyderabad'
    )
    RETURNING id, owner_id, title, type, area, city
),
seed_properties AS (
    -- Newly inserted rows are obtained from RETURNING because data-modifying
    -- CTE changes are not visible through a second scan of properties here.
    SELECT i.id AS property_id, s.amenity_names
    FROM inserted i
    JOIN seed s
      ON s.title = i.title
     AND s.type = i.type
     AND s.area = i.area
     AND i.city = 'Hyderabad'
    JOIN owner o ON o.id = i.owner_id

    UNION ALL

    -- Existing rows with the same documented stable key are included so an
    -- accidental rerun can restore any missing amenity links.
    SELECT p.id AS property_id, s.amenity_names
    FROM seed s
    CROSS JOIN owner o
    JOIN properties p
      ON p.owner_id = o.id
     AND p.title = s.title
     AND p.type = s.type
     AND p.area = s.area
     AND p.city = 'Hyderabad'
),
amenity_links AS (
    SELECT sp.property_id, a.id AS amenity_id
    FROM seed_properties sp
    CROSS JOIN LATERAL unnest(sp.amenity_names) AS requested(name)
    JOIN amenities a ON a.name = requested.name
)
INSERT INTO property_amenities (property_id, amenity_id)
SELECT property_id, amenity_id
FROM amenity_links
ON CONFLICT (property_id, amenity_id) DO NOTHING;
