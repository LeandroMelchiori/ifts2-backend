CREATE TABLE noticias (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    resumen VARCHAR(500) NOT NULL,
    contenido TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_noticias_titulo CHECK (length(btrim(titulo)) > 0),
    CONSTRAINT ck_noticias_resumen CHECK (length(btrim(resumen)) > 0),
    CONSTRAINT ck_noticias_contenido CHECK (length(btrim(contenido)) BETWEEN 1 AND 50000),
    CONSTRAINT ck_noticias_estado CHECK (estado IN ('BORRADOR', 'PUBLICADA', 'ARCHIVADA')),
    CONSTRAINT ck_noticias_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL)
);

CREATE INDEX idx_noticias_publicadas ON noticias (publicada_at DESC, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_noticias_admin ON noticias (created_at DESC, id);
