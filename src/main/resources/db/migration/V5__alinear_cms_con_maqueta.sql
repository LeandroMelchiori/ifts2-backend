ALTER TABLE noticias
    ADD COLUMN area VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN mostrar_en_novedades BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enlace_url VARCHAR(2048),
    ADD COLUMN fecha DATE;

UPDATE noticias SET fecha = (COALESCE(publicada_at, created_at) AT TIME ZONE 'UTC')::date;
ALTER TABLE noticias ALTER COLUMN fecha SET NOT NULL;
ALTER TABLE noticias ADD CONSTRAINT ck_noticias_area
    CHECK (area IN ('GENERAL', 'ALUMNOS', 'DOCENTES', 'TUTORIA', 'EVENTOS'));
CREATE INDEX idx_noticias_area_publica ON noticias (area, publicada_at DESC, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_noticias_novedades ON noticias (publicada_at DESC, id) WHERE estado = 'PUBLICADA' AND mostrar_en_novedades;

ALTER TABLE institucion
    ADD COLUMN busqueda_mapa VARCHAR(500),
    ADD COLUMN sitio_oficial_url VARCHAR(2048),
    ADD COLUMN instagram_url VARCHAR(2048),
    ADD COLUMN instagram_visible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN facebook_url VARCHAR(2048),
    ADD COLUMN facebook_visible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_institucion_instagram_visible CHECK (NOT instagram_visible OR (instagram_url IS NOT NULL AND length(btrim(instagram_url)) > 0)),
    ADD CONSTRAINT ck_institucion_facebook_visible CHECK (NOT facebook_visible OR (facebook_url IS NOT NULL AND length(btrim(facebook_url)) > 0));

-- Un registro tecnico serializa incluso la primera edicion del carrusel vacio.
CREATE TABLE carrusel (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO carrusel (id) VALUES (1);

CREATE TABLE destacados (
    posicion INTEGER PRIMARY KEY CHECK (posicion BETWEEN 1 AND 6),
    noticia_id UUID UNIQUE REFERENCES noticias(id),
    evento_id UUID UNIQUE REFERENCES eventos(id),
    CONSTRAINT ck_destacado_origen CHECK ((noticia_id IS NOT NULL) <> (evento_id IS NOT NULL))
);

CREATE TABLE evento_fotos (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL REFERENCES eventos(id),
    object_key VARCHAR(255) NOT NULL UNIQUE,
    etiqueta VARCHAR(200) NOT NULL CHECK (length(btrim(etiqueta)) BETWEEN 1 AND 200),
    orden INTEGER NOT NULL CHECK (orden BETWEEN 0 AND 10000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_evento_foto_archivo CHECK (object_key ~ '^eventos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$')
);
CREATE INDEX idx_evento_fotos_evento ON evento_fotos (evento_id, orden, id);
