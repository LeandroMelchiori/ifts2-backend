CREATE TABLE panel_role (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE panel_role_permission (
    role_id UUID NOT NULL REFERENCES panel_role(id) ON DELETE CASCADE,
    section VARCHAR(40) NOT NULL CHECK (section IN ('destacados','informacion','contenido','instagram')),
    can_view BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (role_id, section),
    CHECK (NOT can_manage OR can_view)
);

-- No conceder permisos implicitamente a los EDITOR existentes.
ALTER TABLE panel_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE panel_role_permission ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON panel_role, panel_role_permission FROM PUBLIC;
DO $$
DECLARE role_name TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('REVOKE ALL ON panel_role, panel_role_permission FROM %I', role_name);
            IF has_table_privilege(role_name, 'panel_role', 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
               OR has_table_privilege(role_name, 'panel_role_permission', 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') THEN
                RAISE EXCEPTION 'Persisten privilegios heredados de % sobre roles del panel', role_name;
            END IF;
        END IF;
    END LOOP;
END $$;
