# Seguridad de Supabase y recuperacion del CMS

## Limites y responsabilidades

El navegador consume Spring Boot, no las tablas del CMS por REST/GraphQL de
Supabase. Spring Security valida JWT y roles; JDBC accede con el propietario de
las tablas. La clave service_role solo se usa en el adaptador HTTP de Storage.

V7 habilita RLS en las 12 tablas funcionales y revoca grants de tabla Y columna
a PUBLIC, anon, authenticated y service_role en las 13 tablas propias.
flyway_schema_history se protege con REVOKE, sin ALTER TABLE/RLS durante la
migracion para evitar conflicto con los locks de Flyway sobre su propio historial.
Sin policies de acceso, RLS deniega filas a roles ordinarios. El propietario
JDBC conserva acceso: no se usa FORCE ROW LEVEL SECURITY. No se crean roles
Supabase en PostgreSQL local, ni se alteran auth, storage o tablas ajenas.
La migracion falla atomicamente si sobreviven grants heredados de otros roles.

Tambien revoca defaults de tablas/secuencias del creador actual en el esquema.
No revoca defaults globales ni de otros creadores: auditarlos por separado.
Cada tabla futura necesita su propia proteccion en la misma migracion.
V8 agrega panel_role y panel_role_permission con RLS y revocacion de grants;
el alcance actual es 14 tablas funcionales y 15 incluyendo el historial Flyway.
Si se separan usuarios JDBC de runtime y migraciones, disenar sus grants y
policies antes del cambio; un usuario no propietario puede quedar sin acceso.

## Auditoria y despliegue

Auditoria inicial del 2026-09-25 (solo metadatos, antes de V7): las 13 tablas
tenian grants de lectura/escritura para anon/authenticated/service_role y RLS
desactivado. No se consultaron usuarios ni se probo extraccion por Data API;
estos permisos son un riesgo, no evidencia de acceso indebido. No se encontraron
vistas ni funciones SECURITY DEFINER en public. Existian defaults permisivos de
postgres y supabase_admin; V7 solo cambia los del ejecutor actual.
La URL JDBC local no especificaba sslmode; no se leyo la configuracion de Render.
Revisar TLS de ambos entornos sin inferirlo de pg_stat_ssl detras del pooler.
Una conexion de prueba con verify-full + DefaultJavaSSLFactory fallo por cadena
de certificados no confiable (SunCertPathBuilderException) en el JDK local.
No se modifico .env ni se debilito la validacion. Obtener la CA por un canal
autenticado del proveedor y configurar sslrootcert/truststore; repetir la prueba
en local y Render. No confiar en una CA descargada desde una conexion no verificada.
La aplicacion en produccion de V7 debe verificarse con una nueva auditoria;
tener el archivo en Git no implica que los permisos remotos hayan cambiado.

1. Ejecutar `scripts/audit-database-security.sql` en SQL Editor o psql.
   Es de solo lectura y no devuelve passwords, hashes ni contenido de usuarios.
2. Confirmar que frontend/Edge Functions no acceden directamente a tablas CMS.
   Revisar tambien vistas, funciones SECURITY DEFINER, membresias y defaults.
3. Tener copia recuperable y aplicar V7/V8 mediante el despliegue habitual/Flyway.
   No editar migraciones anteriores, usar repair para ocultar errores ni ejecutar
   ddl-auto=update. No aplicar SQL manualmente y luego inventar filas de historial.
4. Repetir auditoria: las 14 tablas funcionales deben tener RLS=true. Las 15 tablas,
   incluido historial, deben tener table_access=false / column_access=false para
   los tres roles de Data API.
5. Probar health, login, lectura publica, administracion y Storage desde el backend.
   Un JWT del CMS no es un JWT de Supabase Auth.

Si nadie usa REST/GraphQL de Supabase, desactivar **Enable Data API** en el
dashboard es otra barrera. Confirmar usos con el companero antes; no sustituye
los controles SQL. No desactivar Storage ni revocar permisos de storage.objects.
Verificar un intento HTTP contra `/rest/v1/usuarios?select=id&limit=0` usando
solo la clave publishable/anon, no service_role: debe ser rechazado. No consultar
emails/password_hash para probarlo. Una respuesta vacia 200 no prueba que todos
los permisos de escritura esten cerrados; complementar con la auditoria SQL.

La existencia de grants inseguros no prueba que alguien haya accedido. Si la
Data API estuvo activa, revisar registros disponibles y tratarlo como posible
exposicion antes de afirmar que no hubo incidentes. Evaluar restablecimiento de
contrasenas de administradores si hubo acceso a hashes; no se almacenan passwords
en claro. No prometer que rotar JWT_SECRET resuelve la exposicion de hashes.

### Conexion y secretos

- Produccion: JDBC con TLS, idealmente sslmode=verify-full y CA de confianza.
  Verificar el hostname exacto. No usar NonValidatingFactory ni degradar TLS ante
  un error. pg_stat_ssl visto detras de un pooler puede describir el tramo
  pooler/Postgres, no el TLS cliente/pooler.
- Preferir rol dedicado no superusuario como evolucion operativa. Actualmente
  Flyway y runtime comparten usuario; separar no es cambiar solo DB_USERNAME.
- Deshabilitar BOOTSTRAP_ADMIN_ENABLED tras crear la cuenta inicial y quitar su
  password del entorno de produccion. Mantener STORAGE_TEST_ENDPOINTS_ENABLED=false.
- Claves DB, JWT, Meta y service_role solo en el servidor/gestor de secretos.
  Proteger tambien .env local. No incluir secretos ni dumps en artifacts de CI.
- El bucket publico sirve archivos a quien conozca su URL: no guardar material
  confidencial ni asumir que ocultar contenido revoca archivos/caches existentes.

## Politica de respaldos propuesta

Pendiente de aprobar responsables, destino y automatizacion. No hay tareas
programadas creadas por esta guia. Objetivos iniciales, aun no garantizados:
RPO de 24 horas y RTO de 4 horas. Ajustarlos con resultados de ensayos reales.

- Base y archivos: diario y antes de migraciones/cambios destructivos.
- Retencion propuesta: 7 diarios y 4 semanales, mas la copia previa a despliegues
  hasta validar el cambio. No borrar la ultima copia verificada.
- Una copia fuera del proveedor y del equipo de desarrollo, cifrada, con acceso
  exclusivo de responsables designados y credenciales recuperables por el IFTS.
- Registrar fecha, alcance, hash, resultado, version del backend, version de
  PostgreSQL y fecha del ultimo ensayo. Alertar ante fallos, no solo programar.
- Ensayo mensual y antes de cambios importantes, en entorno aislado.

Supabase recomienda exportaciones externas para proyectos Free. Los respaldos
de la base **no contienen los archivos de Storage**. No asumir backups diarios
o PITR disponibles sin comprobar el plan y la configuracion del proyecto.

## Respaldo PostgreSQL

`scripts/Backup-CmsDatabase.ps1` usa herramientas nativas PostgreSQL, no Docker.
Exporta solamente las 15 tablas propias, incluidos roles del panel y permisos,
en formato custom (esquema, datos,
indices, constraints e historial Flyway). No es una copia integral de Supabase:
excluye roles PostgreSQL, secretos de entorno, auth/storage internos, funciones y extensiones ajenas.
Requiere esquema V8 o posterior; para un respaldo previo a V8 usar la version
del script correspondiente al esquema anterior. No eliminar tablas de la lista
para ocultar un error de esquema.
Al agregar tablas, tipos propios o funciones al CMS, actualizar este alcance y
su prueba de restauracion. Preferir pg_dump de la misma version mayor del servidor;
nunca uno anterior. Validar compatibilidad con la version destino.

Preparar una carpeta privada fuera del repo, en un volumen cifrado. El script
NO cifra los dumps: contienen emails y hashes BCrypt y siguen siendo sensibles.
No usar Downloads, carpeta publica, bucket publico ni sincronizacion personal.
Proteger un PGPASSFILE externo con ACL de usuario; su formato es
host:port:database:user:password (escapar `:` y `\` segun libpq). No pasarlo por chat.

Configurar estas variables en la sesion sin escribir la password en comandos:

```powershell
$env:PGHOST = '<host directo o session pooler>'
$env:PGPORT = '5432'
$env:PGDATABASE = 'postgres'
$env:PGUSER = '<usuario de conexion>'
$env:PGSSLMODE = 'verify-full'
$env:PGSSLROOTCERT = '<ruta al certificado raiz confiable>'
$env:PGPASSFILE = '<ruta privada al archivo de credenciales>'
.\scripts\Backup-CmsDatabase.ps1 -PgBin '<carpeta bin de PostgreSQL>' -OutputDirectory '<carpeta cifrada fuera del repo>'
```

Usar conexion directa o session pooler, no transaction pooler. La URL JDBC no se
pasa a pg_dump. El script verifica codigo de salida, lectura del archivo y SHA256;
solo esto NO prueba recuperacion y el manifiesto indica restoreTested=false.
Los fallos no dejan un archivo .dump presentado como completo. No poda copias.

## Archivos de Storage

Preparar copia separada del bucket completo (incluidos archivos no referenciados),
manteniendo objectKey, tipo MIME, tamano y checksum por objeto. Usar exportacion
autenticada o una herramienta compatible con S3; credenciales solo en el equipo
operativo. El destino de backup debe ser privado aunque el bucket origen sea publico.
Registrar configuracion del bucket, MIME/limites y policies por separado.
No alcanza guardar un listado de nombres, URLs publicas o la tabla storage.objects.

Para un punto de recuperacion coherente, pausar escrituras administrativas,
sincronizaciones y uploads/deletes durante la copia coordinada de DB y archivos.
Registrar inicio/fin y verificar todos los checksums antes de reanudar. No hay
snapshot atomico distribuido entre PostgreSQL y Storage.

Todavia no se implemento un exportador automatico de Storage ni se produjo una
copia institucional real: requieren elegir destino seguro, acceso y responsable.

## Ensayo de restauracion (nunca sobre produccion)

1. Verificar origen confiable, integridad SHA256 y disponibilidad de DB + archivos.
   Restaurar un dump ejecuta SQL: no usar backups de origen desconocido.
2. Crear una base/proyecto de recuperacion separado y vacio, sin frontend, sin
   tareas Meta y con Data API desactivada hasta completar permisos. Configurar
   PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSFILE al DESTINO y revisar dos veces.
3. Restaurar como propietario futuro de las tablas con cliente compatible:

```powershell
& '<pg-bin>\pg_restore.exe' --no-password --dbname=$env:PGDATABASE --no-owner --no-privileges --single-transaction --exit-on-error '<backup.dump>'
if ($LASTEXITCODE -ne 0) { throw 'Restauracion fallida: no continuar' }
& '<pg-bin>\psql.exe' --no-password --set=ON_ERROR_STOP=1 --single-transaction --file='src/main/resources/db/migration/V7__proteger_tablas_cms.sql'
if ($LASTEXITCODE -ne 0) { throw 'Permisos sin verificar: no habilitar acceso' }
& '<pg-bin>\psql.exe' --no-password --set=ON_ERROR_STOP=1 --single-transaction --file='scripts/restore-panel-permissions.sql'
if ($LASTEXITCODE -ne 0) { throw 'Permisos del panel sin verificar: no habilitar acceso' }
```

No usar --clean ni restaurar encima de una base con datos. --no-privileges evita
recrear ACL de otro entorno, pero los defaults del destino pueden conceder
permisos: reaplicar V7 y restore-panel-permissions.sql incluso si el historial
restaurado ya indica version 8. El segundo script es idempotente y requiere las
tablas V8; para backups anteriores, ejecutarlo luego de aplicar V8 con Flyway.
No ejecutar V8 manualmente sobre tablas restauradas: contiene CREATE TABLE.
El backup cubre public; para otro esquema adaptar y ensayar explicitamente.

4. Restaurar los objetos al bucket de recuperacion con las MISMAS claves, MIME y
   contenido; verificar checksums y referencias del CMS, no solo numero de archivos.
5. Arrancar el mismo commit/version del backup apuntando a la base restaurada,
   con secretos propios de recuperacion, META_ENABLED=false y bootstrap desactivado.
   Flyway debe validar sin repair. Para un backup anterior a V7, permitir que
   Flyway aplique migraciones pendientes despues de la restauracion aislada.
6. Ejecutar auditoria, login, roles ADMIN/EDITOR, lectura de contenido, fotos/PDF y
   medicion de Storage. Verificar usuarios, estados, destacados y filas relevantes
   sin publicar datos sensibles en registros del ensayo.
7. Registrar duracion, resultados y fallos. Solo declarar recuperable ese conjunto
   de backup cuando DB y archivos hayan pasado; conservar la evidencia privada.

En un incidente, restaurar primero a otro destino. El cambio de DB_URL y bucket
en Render requiere aprobacion y ventana de mantenimiento; preservar el origen,
probar antes de cambiar trafico y no volver a un origen desactualizado tras aceptar
nuevas escrituras sin una estrategia explicita de reconciliacion.

## Pruebas locales

Los tests de migracion se ejecutan siempre con PostgreSQL temporal real, incluyendo
roles ordinarios, grants por columna, defaults y fallo por permisos heredados.
El ensayo adicional necesita pg_dump/pg_restore y PowerShell:

```powershell
$env:PG_BIN = 'C:\Program Files\PostgreSQL\18\bin'
.\mvnw.cmd -B -ntp '-Dtest=DatabaseSecurityMigrationTest,DatabaseRecoveryTest' test
```

DatabaseRecoveryTest usa SOLO datos sinteticos y dos bases temporales locales,
prueba el script, checksum, restauracion, constraints e historial Flyway, y rechazo
de destinos dentro del repo. Sin PG_BIN se omite este ensayo, no la suite de permisos.
No prueba restauracion de Storage ni es evidencia de un backup real de produccion.

## Referencias

- [Supabase: seguridad de la Data API](https://supabase.com/docs/guides/api/securing-your-api)
- [Supabase: respaldos y limites de Storage](https://supabase.com/docs/guides/platform/backups)
- [Supabase: conexiones PostgreSQL](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [PostgreSQL: pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html)
- [PostgreSQL: pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html)
- [pgJDBC: validacion TLS](https://jdbc.postgresql.org/documentation/ssl/)
