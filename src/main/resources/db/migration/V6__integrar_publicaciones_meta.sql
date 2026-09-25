CREATE TABLE meta_posts (
    id VARCHAR(40) PRIMARY KEY CHECK (id ~ '^[0-9]{1,40}$'),
    texto TEXT NOT NULL CHECK (length(texto) <= 10000),
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO', 'CAROUSEL_ALBUM')),
    fecha_publicacion TIMESTAMP WITH TIME ZONE NOT NULL,
    link_original VARCHAR(2048) NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT FALSE,
    object_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_meta_visible_archivo CHECK (NOT visible OR object_key IS NOT NULL),
    CONSTRAINT ck_meta_archivo CHECK (object_key IS NULL OR object_key ~ '^meta/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$')
);
CREATE INDEX idx_meta_publicaciones ON meta_posts (fecha_publicacion DESC, id);
CREATE INDEX idx_meta_visibles ON meta_posts (fecha_publicacion DESC, id) WHERE visible;

ALTER TABLE destacados ADD COLUMN meta_post_id VARCHAR(40) UNIQUE REFERENCES meta_posts(id);
ALTER TABLE destacados DROP CONSTRAINT ck_destacado_origen;
ALTER TABLE destacados ADD CONSTRAINT ck_destacado_origen CHECK (
    (noticia_id IS NOT NULL)::integer + (evento_id IS NOT NULL)::integer + (meta_post_id IS NOT NULL)::integer = 1
);
