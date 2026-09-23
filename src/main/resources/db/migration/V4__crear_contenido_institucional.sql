CREATE TABLE eventos (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    resumen VARCHAR(500) NOT NULL,
    descripcion TEXT NOT NULL,
    fecha_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_fin TIMESTAMP WITH TIME ZONE NOT NULL,
    lugar VARCHAR(300) NOT NULL,
    portada_object_key VARCHAR(255) UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_eventos_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_eventos_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_eventos_titulo CHECK (length(btrim(titulo)) BETWEEN 1 AND 200),
    CONSTRAINT ck_eventos_resumen CHECK (length(btrim(resumen)) BETWEEN 1 AND 500),
    CONSTRAINT ck_eventos_descripcion CHECK (length(btrim(descripcion)) BETWEEN 1 AND 20000),
    CONSTRAINT ck_eventos_lugar CHECK (length(btrim(lugar)) BETWEEN 1 AND 300),
    CONSTRAINT ck_eventos_periodo CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ck_eventos_archivo CHECK (portada_object_key IS NULL OR portada_object_key ~ '^eventos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$')
);

CREATE INDEX idx_eventos_publicados ON eventos (fecha_inicio, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_eventos_admin ON eventos (created_at DESC, id);

CREATE TABLE carreras (
    id UUID PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    titulo_otorgado VARCHAR(200) NOT NULL,
    descripcion TEXT NOT NULL,
    duracion VARCHAR(100) NOT NULL,
    modalidad VARCHAR(100) NOT NULL,
    requisitos_ingreso TEXT,
    orden INTEGER NOT NULL,
    imagen_object_key VARCHAR(255) UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_carreras_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_carreras_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_carreras_nombre CHECK (length(btrim(nombre)) BETWEEN 1 AND 200),
    CONSTRAINT ck_carreras_titulo_otorgado CHECK (length(btrim(titulo_otorgado)) BETWEEN 1 AND 200),
    CONSTRAINT ck_carreras_descripcion CHECK (length(btrim(descripcion)) BETWEEN 1 AND 20000),
    CONSTRAINT ck_carreras_duracion CHECK (length(btrim(duracion)) BETWEEN 1 AND 100),
    CONSTRAINT ck_carreras_modalidad CHECK (length(btrim(modalidad)) BETWEEN 1 AND 100),
    CONSTRAINT ck_carreras_requisitos_ingreso CHECK (requisitos_ingreso IS NULL OR length(requisitos_ingreso) <= 10000),
    CONSTRAINT ck_carreras_orden CHECK (orden BETWEEN 0 AND 10000),
    CONSTRAINT ck_carreras_archivo CHECK (imagen_object_key IS NULL OR imagen_object_key ~ '^carreras/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$')
);

CREATE INDEX idx_carreras_publicados ON carreras (orden, nombre, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_carreras_admin ON carreras (created_at DESC, id);

CREATE TABLE autoridades (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    cargo VARCHAR(150) NOT NULL,
    descripcion VARCHAR(2000),
    orden INTEGER NOT NULL,
    foto_object_key VARCHAR(255) UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_autoridades_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_autoridades_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_autoridades_nombre CHECK (length(btrim(nombre)) BETWEEN 1 AND 100),
    CONSTRAINT ck_autoridades_apellido CHECK (length(btrim(apellido)) BETWEEN 1 AND 100),
    CONSTRAINT ck_autoridades_cargo CHECK (length(btrim(cargo)) BETWEEN 1 AND 150),
    CONSTRAINT ck_autoridades_descripcion CHECK (descripcion IS NULL OR length(descripcion) <= 2000),
    CONSTRAINT ck_autoridades_orden CHECK (orden BETWEEN 0 AND 10000),
    CONSTRAINT ck_autoridades_archivo CHECK (foto_object_key IS NULL OR foto_object_key ~ '^autoridades/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$')
);

CREATE INDEX idx_autoridades_publicados ON autoridades (orden, apellido, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_autoridades_admin ON autoridades (created_at DESC, id);

CREATE TABLE enlaces (
    id UUID PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion VARCHAR(500),
    url VARCHAR(2048) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    orden INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_enlaces_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_enlaces_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_enlaces_titulo CHECK (length(btrim(titulo)) BETWEEN 1 AND 150),
    CONSTRAINT ck_enlaces_descripcion CHECK (descripcion IS NULL OR length(descripcion) <= 500),
    CONSTRAINT ck_enlaces_url CHECK (length(btrim(url)) BETWEEN 1 AND 2048),
    CONSTRAINT ck_enlaces_categoria CHECK (length(btrim(categoria)) BETWEEN 1 AND 100),
    CONSTRAINT ck_enlaces_orden CHECK (orden BETWEEN 0 AND 10000)
);

CREATE INDEX idx_enlaces_publicados ON enlaces (orden, titulo, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_enlaces_admin ON enlaces (created_at DESC, id);

CREATE TABLE documentos (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion VARCHAR(2000),
    tipo VARCHAR(30) NOT NULL,
    archivo_object_key VARCHAR(255) UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_documentos_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_documentos_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_documentos_titulo CHECK (length(btrim(titulo)) BETWEEN 1 AND 200),
    CONSTRAINT ck_documentos_descripcion CHECK (descripcion IS NULL OR length(descripcion) <= 2000),
    CONSTRAINT ck_documentos_tipo CHECK (tipo IN ('PLAN_ESTUDIO','CORRELATIVIDADES','REGLAMENTO','CALENDARIO','HORARIO','RESOLUCION','FORMULARIO','INSTRUCTIVO','OTRO')),
    CONSTRAINT ck_documentos_archivo_publicado CHECK (estado <> 'PUBLICADA' OR archivo_object_key IS NOT NULL),
    CONSTRAINT ck_documentos_archivo CHECK (archivo_object_key IS NULL OR archivo_object_key ~ '^documentos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.pdf$')
);

CREATE INDEX idx_documentos_publicados ON documentos (publicada_at DESC, id) WHERE estado = 'PUBLICADA';
CREATE INDEX idx_documentos_admin ON documentos (created_at DESC, id);

CREATE TABLE institucion (
    id UUID PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    descripcion TEXT NOT NULL,
    direccion VARCHAR(300) NOT NULL,
    email VARCHAR(254) NOT NULL,
    telefono VARCHAR(50),
    horarios_atencion VARCHAR(500),
    singleton BOOLEAN NOT NULL DEFAULT TRUE UNIQUE CHECK (singleton = TRUE),
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicada_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_institucion_estado CHECK (estado IN ('BORRADOR','PUBLICADA','ARCHIVADA')),
    CONSTRAINT ck_institucion_publicacion CHECK (estado <> 'PUBLICADA' OR publicada_at IS NOT NULL),
    CONSTRAINT ck_institucion_nombre CHECK (length(btrim(nombre)) BETWEEN 1 AND 200),
    CONSTRAINT ck_institucion_descripcion CHECK (length(btrim(descripcion)) BETWEEN 1 AND 20000),
    CONSTRAINT ck_institucion_direccion CHECK (length(btrim(direccion)) BETWEEN 1 AND 300),
    CONSTRAINT ck_institucion_email CHECK (length(btrim(email)) BETWEEN 1 AND 254),
    CONSTRAINT ck_institucion_telefono CHECK (telefono IS NULL OR length(telefono) <= 50),
    CONSTRAINT ck_institucion_horarios_atencion CHECK (horarios_atencion IS NULL OR length(horarios_atencion) <= 500)
);
