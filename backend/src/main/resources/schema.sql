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
    requires_manual_identification BOOLEAN NOT NULL DEFAULT FALSE,
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
    status TEXT NOT NULL DEFAULT 'published',
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (observation_id) REFERENCES observation(observation_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (plant_species_id) REFERENCES plant_species(plant_species_id) ON DELETE SET NULL,
    CONSTRAINT chk_publication_status
        CHECK (status IN ('draft', 'published'))
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

CREATE TABLE IF NOT EXISTS saved_visit_target (
    saved_visit_target_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER NOT NULL,
    observation_id INTEGER,
    plant_species_id INTEGER,
    publication_id INTEGER,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (observation_id) REFERENCES observation(observation_id) ON DELETE CASCADE,
    FOREIGN KEY (plant_species_id) REFERENCES plant_species(plant_species_id) ON DELETE CASCADE,
    FOREIGN KEY (publication_id) REFERENCES publication(publication_id) ON DELETE CASCADE,
    CONSTRAINT chk_saved_visit_target_single_target
        CHECK (num_nonnulls(observation_id, plant_species_id, publication_id) = 1)
);

CREATE TABLE IF NOT EXISTS route_plan (
    route_plan_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    start_label TEXT,
    start_latitude NUMERIC(10,7),
    start_longitude NUMERIC(10,7),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_route_plan_name_not_empty
        CHECK (char_length(trim(name)) > 0),
    CONSTRAINT chk_route_plan_latitude_range
        CHECK (
            start_latitude IS NULL OR
            (start_latitude >= -90 AND start_latitude <= 90)
        ),
    CONSTRAINT chk_route_plan_longitude_range
        CHECK (
            start_longitude IS NULL OR
            (start_longitude >= -180 AND start_longitude <= 180)
        ),
    CONSTRAINT chk_route_plan_start_coordinates_pair
        CHECK ((start_latitude IS NULL) = (start_longitude IS NULL))
);

CREATE TABLE IF NOT EXISTS route_plan_point (
    route_plan_point_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_plan_id INTEGER NOT NULL,
    saved_visit_target_id INTEGER NOT NULL,
    visit_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (route_plan_id) REFERENCES route_plan(route_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (saved_visit_target_id) REFERENCES saved_visit_target(saved_visit_target_id) ON DELETE RESTRICT,
    CONSTRAINT chk_route_plan_point_visit_order
        CHECK (visit_order > 0),
    CONSTRAINT uq_route_plan_point_saved_target
        UNIQUE (route_plan_id, saved_visit_target_id),
    CONSTRAINT uq_route_plan_point_visit_order
        UNIQUE (route_plan_id, visit_order)
);

ALTER TABLE observation
    ADD COLUMN IF NOT EXISTS requires_manual_identification BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE publication
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'published';

ALTER TABLE publication
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

ALTER TABLE publication
    DROP CONSTRAINT IF EXISTS chk_publication_status;

ALTER TABLE publication
    ADD CONSTRAINT chk_publication_status
        CHECK (status IN ('draft', 'published'));

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_observation_user_id ON observation(user_id);
CREATE INDEX IF NOT EXISTS idx_observation_plant_species_id ON observation(plant_species_id);
CREATE INDEX IF NOT EXISTS idx_observation_requires_manual_identification ON observation(requires_manual_identification);
CREATE INDEX IF NOT EXISTS idx_observation_is_published ON observation(is_published);
CREATE INDEX IF NOT EXISTS idx_observation_sync_status ON observation(sync_status);
CREATE INDEX IF NOT EXISTS idx_observation_last_sync_attempt_at ON observation(last_sync_attempt_at);
CREATE INDEX IF NOT EXISTS idx_observation_device_observation_id ON observation(device_observation_id);
CREATE INDEX IF NOT EXISTS idx_observation_observed_at ON observation(observed_at);
CREATE INDEX IF NOT EXISTS idx_observation_captured_at ON observation(captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_observation_lat_lon ON observation(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_observation_image_observation_id ON observation_image(observation_id);
CREATE INDEX IF NOT EXISTS idx_observation_species_observed_at ON observation(plant_species_id, observed_at DESC, observation_id DESC);
CREATE INDEX IF NOT EXISTS idx_observation_species_captured_at ON observation(plant_species_id, captured_at DESC, observation_id DESC);
CREATE INDEX IF NOT EXISTS idx_observation_image_observation_order ON observation_image(observation_id, observation_image_id);
CREATE INDEX IF NOT EXISTS idx_publication_user_id ON publication(user_id);
CREATE INDEX IF NOT EXISTS idx_publication_plant_species_id ON publication(plant_species_id);
CREATE INDEX IF NOT EXISTS idx_publication_published_at ON publication(published_at);
CREATE INDEX IF NOT EXISTS idx_publication_status_published_at ON publication(status, published_at DESC, publication_id DESC);
CREATE INDEX IF NOT EXISTS idx_publication_image_publication_id ON publication_image(publication_id);
CREATE INDEX IF NOT EXISTS idx_publication_image_publication_order ON publication_image(publication_id, publication_image_id);
CREATE INDEX IF NOT EXISTS idx_plant_species_family ON plant_species(family);
CREATE INDEX IF NOT EXISTS idx_plant_species_genus ON plant_species(genus);
CREATE INDEX IF NOT EXISTS idx_plant_species_species ON plant_species(species);
CREATE INDEX IF NOT EXISTS idx_saved_visit_target_user_created_at ON saved_visit_target(user_id, created_at DESC, saved_visit_target_id DESC);
CREATE INDEX IF NOT EXISTS idx_saved_visit_target_user_observation ON saved_visit_target(user_id, observation_id);
CREATE INDEX IF NOT EXISTS idx_saved_visit_target_user_publication ON saved_visit_target(user_id, publication_id);
CREATE INDEX IF NOT EXISTS idx_saved_visit_target_user_species ON saved_visit_target(user_id, plant_species_id);
CREATE INDEX IF NOT EXISTS idx_route_plan_user_updated_at ON route_plan(user_id, updated_at DESC, route_plan_id DESC);
CREATE INDEX IF NOT EXISTS idx_route_plan_point_route_order ON route_plan_point(route_plan_id, visit_order, route_plan_point_id);
CREATE INDEX IF NOT EXISTS idx_route_plan_point_saved_visit_target ON route_plan_point(saved_visit_target_id);
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

CREATE OR REPLACE VIEW resolved_visit_target AS
SELECT svt.saved_visit_target_id,
       svt.user_id,
       CASE
           WHEN svt.observation_id IS NOT NULL THEN 'observation'
           WHEN svt.publication_id IS NOT NULL THEN 'publication'
           WHEN svt.plant_species_id IS NOT NULL THEN 'species'
           ELSE 'unknown'
       END AS target_type,
       CASE
           WHEN svt.observation_id IS NOT NULL THEN COALESCE(
               NULLIF(obs_target.enriched_common_name, ''),
               NULLIF(species_target.common_name, ''),
               NULLIF(species_target.scientific_name, ''),
               'Observacao botanica'
           )
           WHEN svt.publication_id IS NOT NULL THEN COALESCE(
               NULLIF(publication_target.title, ''),
               NULLIF(species_target.common_name, ''),
               NULLIF(publication_observation.enriched_common_name, ''),
               NULLIF(species_target.scientific_name, ''),
               'Publicacao botanica'
           )
           WHEN svt.plant_species_id IS NOT NULL THEN COALESCE(
               NULLIF(species_target.common_name, ''),
               NULLIF(species_target.scientific_name, ''),
               'Especie selecionada'
           )
           ELSE 'Alvo de visita'
       END AS title,
       CASE
           WHEN svt.observation_id IS NOT NULL THEN COALESCE(
               NULLIF(obs_target.enriched_scientific_name, ''),
               NULLIF(obs_target.predicted_scientific_name, ''),
               NULLIF(species_target.scientific_name, ''),
               'Observacao com coordenadas'
           )
           WHEN svt.publication_id IS NOT NULL THEN COALESCE(
               NULLIF(species_target.scientific_name, ''),
               NULLIF(publication_observation.enriched_scientific_name, ''),
               NULLIF(publication_observation.predicted_scientific_name, ''),
               'Publicacao associada a observacao'
           )
           WHEN svt.plant_species_id IS NOT NULL THEN COALESCE(
               NULLIF(species_target.scientific_name, ''),
               'Sem classificacao cientifica'
           )
           ELSE NULLIF(svt.notes, '')
       END AS subtitle,
       svt.notes,
       svt.observation_id,
       COALESCE(
           svt.plant_species_id,
           obs_target.plant_species_id,
           publication_target.plant_species_id,
           publication_observation.plant_species_id
       ) AS plant_species_id,
       svt.publication_id,
       COALESCE(
           obs_target.latitude,
           publication_observation.latitude,
           species_observation.latitude
       ) AS latitude,
       COALESCE(
           obs_target.longitude,
           publication_observation.longitude,
           species_observation.longitude
       ) AS longitude,
       CASE
           WHEN svt.observation_id IS NOT NULL THEN COALESCE(
               obs_target_image.image_path,
               obs_target.image_uri,
               obs_target.enriched_photo_url
           )
           WHEN svt.publication_id IS NOT NULL THEN COALESCE(
               publication_target_image.image_path,
               publication_observation_image.image_path,
               publication_observation.image_uri,
               publication_observation.enriched_photo_url
           )
           WHEN svt.plant_species_id IS NOT NULL THEN COALESCE(
               species_observation_image.image_path,
               species_observation.image_uri,
               species_observation.enriched_photo_url
           )
           ELSE NULL
       END AS image_path,
       svt.created_at
FROM saved_visit_target svt
LEFT JOIN observation obs_target ON obs_target.observation_id = svt.observation_id
LEFT JOIN publication publication_target ON publication_target.publication_id = svt.publication_id
LEFT JOIN observation publication_observation ON publication_observation.observation_id = publication_target.observation_id
LEFT JOIN plant_species species_target ON species_target.plant_species_id = COALESCE(
    svt.plant_species_id,
    obs_target.plant_species_id,
    publication_target.plant_species_id,
    publication_observation.plant_species_id
)
LEFT JOIN LATERAL (
    SELECT oi.image_path
    FROM observation_image oi
    WHERE oi.observation_id = obs_target.observation_id
    ORDER BY oi.observation_image_id ASC
    LIMIT 1
) obs_target_image ON TRUE
LEFT JOIN LATERAL (
    SELECT pi.image_path
    FROM publication_image pi
    WHERE pi.publication_id = publication_target.publication_id
    ORDER BY pi.publication_image_id ASC
    LIMIT 1
) publication_target_image ON TRUE
LEFT JOIN LATERAL (
    SELECT oi.image_path
    FROM observation_image oi
    WHERE oi.observation_id = publication_observation.observation_id
    ORDER BY oi.observation_image_id ASC
    LIMIT 1
) publication_observation_image ON TRUE
LEFT JOIN LATERAL (
    SELECT o.observation_id,
           o.latitude,
           o.longitude,
           o.image_uri,
           o.enriched_photo_url
    FROM observation o
    WHERE o.plant_species_id = svt.plant_species_id
      AND o.latitude IS NOT NULL
      AND o.longitude IS NOT NULL
    ORDER BY o.observed_at DESC NULLS LAST, o.observation_id DESC
    LIMIT 1
) species_observation ON TRUE
LEFT JOIN LATERAL (
    SELECT oi.image_path
    FROM observation_image oi
    WHERE oi.observation_id = species_observation.observation_id
    ORDER BY oi.observation_image_id ASC
    LIMIT 1
) species_observation_image ON TRUE;
