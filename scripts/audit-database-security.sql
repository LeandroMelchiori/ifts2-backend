-- Solo metadatos. Ejecutar en SQL Editor o psql; no consulta registros del CMS.
BEGIN READ ONLY;

SELECT current_user AS connection_role, rolsuper, rolbypassrls
FROM pg_roles WHERE rolname = current_user;

WITH cms AS (
    SELECT c.oid, c.relname, c.relowner, c.relrowsecurity
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'r'
      AND c.relname IN ('usuarios','noticias','eventos','carreras','autoridades',
          'enlaces','documentos','institucion','carrusel','destacados','evento_fotos',
          'meta_posts','panel_role','panel_role_permission','flyway_schema_history')
)
SELECT c.relname, pg_get_userbyid(c.relowner) AS owner, c.relrowsecurity AS rls,
       r.rolname,
       has_table_privilege(r.oid, c.oid, 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') AS table_access,
       has_any_column_privilege(r.oid, c.oid, 'SELECT,INSERT,UPDATE,REFERENCES') AS column_access
FROM cms c CROSS JOIN pg_roles r
WHERE r.rolname IN ('anon','authenticated','service_role')
ORDER BY c.relname, r.rolname;

-- Revisar tambien grants globales o de otros creadores, que V7 no modifica.
SELECT pg_get_userbyid(d.defaclrole) AS creator, COALESCE(n.nspname, 'GLOBAL') AS schema,
       d.defaclobjtype, CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS grantee,
       a.privilege_type
FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace
CROSS JOIN LATERAL aclexplode(d.defaclacl) a
WHERE n.nspname = 'public' OR d.defaclnamespace = 0;

SELECT schemaname, tablename, policyname, roles, cmd
FROM pg_policies WHERE schemaname IN ('public','storage')
ORDER BY schemaname, tablename, policyname;

-- Las funciones SECURITY DEFINER y vistas requieren revision separada.
SELECT n.nspname, p.proname, p.prosecdef
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.prosecdef;
SELECT schemaname, viewname FROM pg_views WHERE schemaname = 'public';

SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;
ROLLBACK;
