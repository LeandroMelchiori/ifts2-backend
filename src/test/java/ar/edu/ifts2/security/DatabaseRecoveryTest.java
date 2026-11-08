package ar.edu.ifts2.security;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// Optional operational rehearsal: needs native pg_dump/pg_restore and PowerShell.
@EnabledIfEnvironmentVariable(named = "PG_BIN", matches = ".+")
class DatabaseRecoveryTest {
    @TempDir Path directory;

    @Test
    void backupScriptRestoresDataConstraintsAndFlywayIntoAnIsolatedDatabase() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setPort(0).start()) {
            var source = pg.getPostgresDatabase();
            Flyway.configure().dataSource(source).load().migrate();
            var jdbc = new JdbcTemplate(source);
            var usuarioId = java.util.UUID.randomUUID();
            String fixtureHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
                    .encode(java.util.UUID.randomUUID().toString());
            jdbc.update("insert into usuarios(id,nombre,apellido,email,password_hash,rol,activo,created_at,updated_at) values (?, 'Backup', 'Fixture', 'backup@example.test', ?, 'EDITOR', true, now(), now())", usuarioId, fixtureHash);
            ar.edu.ifts2.support.PanelRoleFixtures.grantAll(jdbc, usuarioId);
            jdbc.execute("insert into meta_posts (id,texto,media_type,fecha_publicacion,link_original,created_at,updated_at) values ('123','Fixture recuperacion','IMAGE',now(),'https://www.instagram.com/p/test/',now(),now())");
            jdbc.execute("create table unrelated_secret (id integer)");
            var script = Path.of("scripts", "Backup-CmsDatabase.ps1").toAbsolutePath().toString();
            var shell = System.getProperty("os.name").startsWith("Windows") ? "powershell.exe" : "pwsh";
            var backup = new String[]{shell, "-NoProfile", "-NonInteractive", "-File", script,
                    "-PgBin", System.getenv("PG_BIN"), "-OutputDirectory", directory.toString()};
            assertThat(run(pg, "postgres", backup)).isZero();
            Path dump;
            try (var files = Files.list(directory)) {
                dump = files.filter(p -> p.toString().endsWith(".dump")).findFirst().orElseThrow();
            }
            var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(Path.of(dump + ".json")).replace("\uFEFF", ""));
            var hash = java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dump)));
            assertThat(manifest.path("sha256").asText()).isEqualTo(hash);
            assertThat(manifest.path("restoreTested").asBoolean()).isFalse();

            jdbc.execute("create database recovery");
            var restore = Path.of(System.getenv("PG_BIN"), System.getProperty("os.name").startsWith("Windows") ? "pg_restore.exe" : "pg_restore");
            assertThat(run(pg, "recovery", restore.toString(), "--no-password", "--dbname=recovery",
                    "--no-owner", "--no-privileges", "--single-transaction", "--exit-on-error", dump.toString())).isZero();
            var target = pg.getDatabase("postgres", "recovery");
            var recovered = new JdbcTemplate(target);
            // Simular grants heredados de los defaults del destino, incluidos por columna.
            recovered.execute("create role anon");
            recovered.execute("grant all on panel_role, panel_role_permission to anon");
            recovered.execute("grant select (name) on panel_role to public");
            // Restores omit ACLs. Apply V7 again even when Flyway history already says 7.
            recovered.execute(Files.readString(Path.of("src/main/resources/db/migration/V7__proteger_tablas_cms.sql")));
            recovered.execute(Files.readString(Path.of("scripts/restore-panel-permissions.sql")));
            Flyway.configure().dataSource(target).load().validate();
            assertThat(recovered.queryForObject("select count(*) from panel_role where usuario_id=?", Integer.class, usuarioId)).isEqualTo(1);
            assertThat(recovered.queryForObject("select count(*) from panel_role_permission where can_view and can_manage", Integer.class)).isEqualTo(4);
            assertThat(recovered.queryForObject("select has_table_privilege('anon','panel_role','SELECT') or has_any_column_privilege('anon','panel_role','SELECT')", Boolean.class)).isFalse();
            assertThat(recovered.queryForObject("select has_table_privilege('anon','panel_role_permission','SELECT')", Boolean.class)).isFalse();
            assertThat(recovered.queryForObject("select texto from meta_posts where id='123'", String.class)).isEqualTo("Fixture recuperacion");
            assertThat(recovered.queryForObject("select count(*) from carrusel", Integer.class)).isEqualTo(1);
            assertThat(recovered.queryForObject("select to_regclass('public.unrelated_secret')", String.class)).isNull();
            assertThat(recovered.queryForObject("select count(*) from pg_tables where schemaname='public' and tablename <> 'flyway_schema_history' and not rowsecurity", Integer.class)).isZero();
            assertThatThrownBy(() -> recovered.update("update meta_posts set visible=true where id='123'"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            var rejected = backup.clone();
            rejected[rejected.length - 1] = Path.of("").toAbsolutePath().toString();
            assertThat(run(pg, "postgres", rejected)).isNotZero();
            assertThat(run(pg, "missing_database", backup)).isNotZero();
            try (var files = Files.list(directory)) {
                assertThat(files.filter(p -> p.toString().endsWith(".partial")).toList()).isEmpty();
            }
        }
    }

    private int run(EmbeddedPostgres pg, String database, String... command) throws Exception {
        var process = new ProcessBuilder(new ArrayList<>(Arrays.asList(command)));
        process.environment().keySet().removeIf(k -> k.startsWith("PG"));
        process.environment().put("PGHOST", "127.0.0.1");
        process.environment().put("PGPORT", String.valueOf(pg.getPort()));
        process.environment().put("PGDATABASE", database);
        process.environment().put("PGUSER", "postgres");
        process.environment().put("PGSSLMODE", "disable");
        var log = Files.createTempFile(directory, "recovery-", ".log");
        process.redirectErrorStream(true).redirectOutput(log.toFile());
        var child = process.start();
        if (!child.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS)) {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            child.destroyForcibly();
            throw new AssertionError("Recovery subprocess timed out");
        }
        System.out.println(Files.readString(log)); // Synthetic local fixtures only.
        return child.exitValue();
    }
}
