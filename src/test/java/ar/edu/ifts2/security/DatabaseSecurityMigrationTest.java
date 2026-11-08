package ar.edu.ifts2.security;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

class DatabaseSecurityMigrationTest {
    @Test
    void migratesWithoutSupabaseRolesAndProtectsEveryCmsTable() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            var db = pg.getPostgresDatabase();
            Flyway.configure().dataSource(db).target("7").load().migrate();
            var jdbc = new JdbcTemplate(db);
            var userId = java.util.UUID.randomUUID();
            String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode(java.util.UUID.randomUUID().toString());
            jdbc.update("insert into usuarios(id,nombre,apellido,email,password_hash,rol,activo,created_at,updated_at) values (?, 'Anterior', 'Editor', 'legacy@example.test', ?, 'EDITOR', true, now(), now())", userId, hash);
            Flyway.configure().dataSource(db).load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select email from usuarios where id=?", String.class, userId)).isEqualTo("legacy@example.test");
            assertThat(jdbc.queryForObject("select password_hash from usuarios where id=?", String.class, userId)).isEqualTo(hash);
            assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='public' and tablename <> 'flyway_schema_history' and not rowsecurity", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from public.carrusel", Integer.class)).isEqualTo(1);
            Flyway.configure().dataSource(db).schemas("cms_test").defaultSchema("cms_test").load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='cms_test' and rowsecurity", Integer.class)).isEqualTo(14);
        }
    }

    @Test
    void revokesTableAndColumnGrantsWithoutBreakingOwnerOrStorage() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            var admin = new JdbcTemplate(pg.getPostgresDatabase());
            admin.execute("create role cms_owner login");
            admin.execute("grant all on schema public to cms_owner");
            for (var role : new String[]{"anon", "authenticated", "service_role"}) {
                admin.execute("create role " + role + (role.equals("service_role") ? " bypassrls" : ""));
                admin.execute("alter default privileges for role cms_owner in schema public grant all on tables to " + role);
            }
            admin.execute("create schema storage");
            admin.execute("create table storage.objects (id integer)");
            admin.execute("grant all on storage.objects to service_role");
            var db = pg.getDatabase("cms_owner", "postgres");
            Flyway.configure().dataSource(db).target("6").load().migrate();
            admin.execute("create table public.unrelated (id integer)");
            admin.execute("grant select on public.unrelated to anon");
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("grant select (email) on usuarios to anon");
            jdbc.execute("grant select (nombre) on usuarios to public");
            Flyway.configure().dataSource(db).load().migrate();
            for (var role : new String[]{"anon", "authenticated", "service_role"}) {
                assertThat(admin.queryForObject("select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind='r' and c.relname <> 'unrelated' and (has_table_privilege(?, c.oid, 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') or has_any_column_privilege(?, c.oid, 'SELECT,INSERT,UPDATE,REFERENCES'))", Integer.class, role, role)).isZero();
                try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                    statement.execute("set role " + role);
                    assertThatThrownBy(() -> statement.executeQuery("select email from public.usuarios"))
                            .isInstanceOf(java.sql.SQLException.class).extracting("SQLState").isEqualTo("42501");
                }
            }
            assertThat(jdbc.update("update carrusel set id=1 where id=1")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from carrusel", Integer.class)).isEqualTo(1);
            jdbc.execute("create table future_table (id integer)");
            assertThat(admin.queryForObject("select has_table_privilege('anon','public.future_table','SELECT')", Boolean.class)).isFalse();
            assertThat(admin.queryForObject("select has_table_privilege('service_role','storage.objects','SELECT')", Boolean.class)).isTrue();
            assertThat(admin.queryForObject("select has_table_privilege('anon','public.unrelated','SELECT')", Boolean.class)).isTrue();
            jdbc.execute("grant select on carrusel to anon");
            try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
                statement.execute("set role anon");
                try (var rows = statement.executeQuery("select count(*) from public.carrusel")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero(); // RLS still protects rows after an accidental grant.
                }
            }
        }
    }

    @Test
    void inheritedPermissionsAbortMigrationAtomically() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            var db = pg.getPostgresDatabase();
            Flyway.configure().dataSource(db).target("6").load().migrate();
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("create role legacy_reader");
            jdbc.execute("create role anon");
            jdbc.execute("grant legacy_reader to anon");
            jdbc.execute("grant select on usuarios to legacy_reader");
            assertThatThrownBy(() -> Flyway.configure().dataSource(db).load().migrate())
                    .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid='public.usuarios'::regclass", Boolean.class)).isFalse();
            assertThat(Flyway.configure().dataSource(db).load().info().current().getVersion().getVersion()).isEqualTo("6");
        }
    }
}
