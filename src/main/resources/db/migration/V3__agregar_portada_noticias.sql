ALTER TABLE noticias ADD COLUMN portada_object_key VARCHAR(255);

ALTER TABLE noticias ADD CONSTRAINT uk_noticias_portada UNIQUE (portada_object_key);
ALTER TABLE noticias ADD CONSTRAINT ck_noticias_portada CHECK (
    portada_object_key IS NULL OR
    portada_object_key ~ '^noticias/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$'
);
