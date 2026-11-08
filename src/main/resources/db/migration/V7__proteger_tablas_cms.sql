-- El CMS se accede por Spring Security + JDBC, nunca por los roles de la Data API.
-- current_schema() conserva compatibilidad con instalaciones fuera de public.
SET LOCAL lock_timeout = '5s';
DO $$
DECLARE
    schema_name TEXT := current_schema();
    table_name TEXT;
    role_name TEXT;
    columns_sql TEXT;
    grantee_sql TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'usuarios', 'noticias', 'eventos', 'carreras', 'autoridades', 'enlaces',
        'documentos', 'institucion', 'carrusel', 'destacados', 'evento_fotos',
        'meta_posts', 'flyway_schema_history'
    ] LOOP
        -- Flyway mantiene locks en su historial mientras ejecuta esta migracion.
        -- REVOKE lo protege sin pedir el lock exclusivo que necesita ALTER TABLE.
        IF table_name <> 'flyway_schema_history' THEN
            EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', schema_name, table_name);
        END IF;
        SELECT string_agg(format('%I', a.attname), ', ' ORDER BY a.attnum)
        INTO columns_sql
        FROM pg_attribute a
        WHERE a.attrelid = format('%I.%I', schema_name, table_name)::regclass
          AND a.attnum > 0 AND NOT a.attisdropped;

        FOREACH role_name IN ARRAY ARRAY['PUBLIC', 'anon', 'authenticated', 'service_role'] LOOP
            IF role_name <> 'PUBLIC' AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
                CONTINUE;
            END IF;
            grantee_sql := CASE WHEN role_name = 'PUBLIC' THEN 'PUBLIC' ELSE quote_ident(role_name) END;
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE %I.%I FROM %s', schema_name, table_name, grantee_sql);
            -- Los grants por columna sobreviven a un REVOKE de tabla.
            EXECUTE format('REVOKE SELECT (%s), INSERT (%s), UPDATE (%s), REFERENCES (%s) ON TABLE %I.%I FROM %s',
                columns_sql, columns_sql, columns_sql, columns_sql, schema_name, table_name, grantee_sql);

            IF role_name <> 'PUBLIC' AND (
                has_table_privilege(role_name, format('%I.%I', schema_name, table_name),
                    'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                OR has_any_column_privilege(role_name, format('%I.%I', schema_name, table_name),
                    'SELECT,INSERT,UPDATE,REFERENCES')
            ) THEN
                RAISE EXCEPTION 'Persisten privilegios heredados de % sobre %.%; revisar membresias antes de migrar',
                    role_name, schema_name, table_name;
            END IF;
        END LOOP;
    END LOOP;

    -- Solo defaults del creador actual y esquema del CMS; no cambia auth/storage.
    FOREACH role_name IN ARRAY ARRAY['PUBLIC', 'anon', 'authenticated', 'service_role'] LOOP
        IF role_name = 'PUBLIC' OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            grantee_sql := CASE WHEN role_name = 'PUBLIC' THEN 'PUBLIC' ELSE quote_ident(role_name) END;
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM %s', schema_name, grantee_sql);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON SEQUENCES FROM %s', schema_name, grantee_sql);
        END IF;
    END LOOP;
END $$;
