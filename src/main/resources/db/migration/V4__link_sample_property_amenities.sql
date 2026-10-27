-- Link the fictional V3 sample properties to amenities already in the V2 catalog.
-- Resolve both properties and amenities by their unique names rather than generated IDs.
WITH sample_property_amenities (property_title, area, amenity_name) AS (
    VALUES
        ('Shared 3-Person PG near Gachibowli', 'Gachibowli', 'WIFI'),
        ('Shared 3-Person PG near Gachibowli', 'Gachibowli', 'FOOD'),
        ('Shared 3-Person PG near Gachibowli', 'Gachibowli', 'PARKING'),
        ('Shared 3-Person PG near Gachibowli', 'Gachibowli', 'CCTV'),
        ('Shared 3-Person PG near Gachibowli', 'Gachibowli', 'POWER_BACKUP'),
        ('Furnished Private Room in Kondapur', 'Kondapur', 'WIFI'),
        ('Furnished Private Room in Kondapur', 'Kondapur', 'AC'),
        ('Furnished Private Room in Kondapur', 'Kondapur', 'GEYSER'),
        ('Furnished Private Room in Kondapur', 'Kondapur', 'WASHING_MACHINE'),
        ('Furnished Private Room in Kondapur', 'Kondapur', 'CCTV'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'WIFI'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'AC'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'TV'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'PARKING'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'POWER_BACKUP'),
        ('2 BHK Rental Home in Miyapur', 'Miyapur', 'WASHING_MACHINE')
)
INSERT INTO property_amenities (property_id, amenity_id)
SELECT p.id, a.id
FROM sample_property_amenities sample
JOIN users u
  ON u.email = 'sample-owner@staymatch.test'
 AND u.role = 'OWNER'
JOIN properties p
  ON p.owner_id = u.id
 AND p.title = sample.property_title
 AND p.area = sample.area
 AND p.city = 'Hyderabad'
JOIN amenities a
  ON a.name = sample.amenity_name
ON CONFLICT (property_id, amenity_id) DO NOTHING;
