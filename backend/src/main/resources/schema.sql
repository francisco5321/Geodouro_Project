CREATE TABLE IF NOT EXISTS app_user (
    user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    is_authenticated BOOLEAN NOT NULL DEFAULT FALSE,
    guest_label TEXT NOT NULL UNIQUE,
    first_name TEXT,
    last_name TEXT,
    email TEXT UNIQUE,
    username TEXT UNIQUE,
    password_hash TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_guest_label_not_empty
        CHECK (char_length(trim(guest_label)) > 0),
    CONSTRAINT chk_authenticated_user_data
        CHECK (
            (is_authenticated = FALSE)
            OR
            (
                is_authenticated = TRUE
                AND first_name IS NOT NULL
                AND last_name IS NOT NULL
                AND email IS NOT NULL
                AND username IS NOT NULL
            )
        )
);

CREATE TABLE IF NOT EXISTS plant_species (
    plant_species_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scientific_name TEXT NOT NULL UNIQUE,
    common_name TEXT,
    family TEXT NOT NULL,
    genus TEXT NOT NULL,
    species TEXT NOT NULL,
    image_count INTEGER NOT NULL DEFAULT 0,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_image_count_non_negative
        CHECK (image_count >= 0)
);

CREATE TABLE IF NOT EXISTS observation (
    observation_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_observation_id UUID UNIQUE,
    user_id INTEGER NOT NULL,
    plant_species_id INTEGER,
    image_uri TEXT,
    captured_at BIGINT,
    predicted_scientific_name TEXT,
    confidence REAL,
    enriched_scientific_name TEXT,
    enriched_common_name TEXT,
    enriched_family TEXT,
    enriched_wikipedia_url TEXT,
    enriched_photo_url TEXT,
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    is_synced BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    last_sync_attempt_at BIGINT,
    notes TEXT,
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (plant_species_id) REFERENCES plant_species(plant_species_id) ON DELETE SET NULL,
    CONSTRAINT chk_confidence_range
        CHECK (
            confidence IS NULL OR
            (confidence >= 0 AND confidence <= 1)
        ),
    CONSTRAINT chk_latitude_range
        CHECK (
            latitude IS NULL OR
            (latitude >= -90 AND latitude <= 90)
        ),
    CONSTRAINT chk_longitude_range
        CHECK (
            longitude IS NULL OR
            (longitude >= -180 AND longitude <= 180)
        ),
    CONSTRAINT chk_sync_status
        CHECK (sync_status IN ('PENDING', 'SYNCED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS observation_image (
    observation_image_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    observation_id INTEGER NOT NULL,
    image_path TEXT NOT NULL,
    thumbnail_path TEXT,
    mime_type TEXT,
    file_size_bytes BIGINT,
    width_px INTEGER,
    height_px INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (observation_id) REFERENCES observation(observation_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS publication (
    publication_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    observation_id INTEGER NOT NULL UNIQUE,
    user_id INTEGER NOT NULL,
    plant_species_id INTEGER,
    title TEXT,
    description TEXT,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (observation_id) REFERENCES observation(observation_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (plant_species_id) REFERENCES plant_species(plant_species_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS publication_image (
    publication_image_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publication_id INTEGER NOT NULL,
    image_path TEXT NOT NULL,
    thumbnail_path TEXT,
    mime_type TEXT,
    file_size_bytes BIGINT,
    width_px INTEGER,
    height_px INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (publication_id) REFERENCES publication(publication_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_observation_user_id ON observation(user_id);
CREATE INDEX IF NOT EXISTS idx_observation_plant_species_id ON observation(plant_species_id);
CREATE INDEX IF NOT EXISTS idx_observation_is_published ON observation(is_published);
CREATE INDEX IF NOT EXISTS idx_observation_sync_status ON observation(sync_status);
CREATE INDEX IF NOT EXISTS idx_observation_last_sync_attempt_at ON observation(last_sync_attempt_at);
CREATE INDEX IF NOT EXISTS idx_observation_device_observation_id ON observation(device_observation_id);
CREATE INDEX IF NOT EXISTS idx_observation_observed_at ON observation(observed_at);
CREATE INDEX IF NOT EXISTS idx_observation_captured_at ON observation(captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_observation_lat_lon ON observation(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_observation_image_observation_id ON observation_image(observation_id);
CREATE INDEX IF NOT EXISTS idx_publication_user_id ON publication(user_id);
CREATE INDEX IF NOT EXISTS idx_publication_plant_species_id ON publication(plant_species_id);
CREATE INDEX IF NOT EXISTS idx_publication_published_at ON publication(published_at);
CREATE INDEX IF NOT EXISTS idx_publication_image_publication_id ON publication_image(publication_id);
CREATE INDEX IF NOT EXISTS idx_plant_species_family ON plant_species(family);
CREATE INDEX IF NOT EXISTS idx_plant_species_genus ON plant_species(genus);
CREATE INDEX IF NOT EXISTS idx_plant_species_species ON plant_species(species);
CREATE OR REPLACE FUNCTION synthetic_device_observation_id(observation_id INTEGER)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
RETURN (
    substring(md5('geodouro-observation:' || observation_id::text), 1, 8) || '-' ||
    substring(md5('geodouro-observation:' || observation_id::text), 9, 4) || '-' ||
    substring(md5('geodouro-observation:' || observation_id::text), 13, 4) || '-' ||
    substring(md5('geodouro-observation:' || observation_id::text), 17, 4) || '-' ||
    substring(md5('geodouro-observation:' || observation_id::text), 21, 12)
)::uuid;
