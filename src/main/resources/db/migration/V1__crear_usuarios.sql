CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_normalizado CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_usuarios_nombre CHECK (length(btrim(nombre)) > 0),
    CONSTRAINT ck_usuarios_apellido CHECK (length(btrim(apellido)) > 0),
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMIN', 'EDITOR')),
    CONSTRAINT ck_usuarios_password_bcrypt CHECK (
        password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
    )
);
