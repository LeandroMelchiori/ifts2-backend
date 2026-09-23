# IFTS N. 2 - Backend institucional

API REST del CMS institucional con autenticacion JWT, administracion de usuarios
ADMIN/EDITOR y almacenamiento desacoplado con una implementacion de Supabase Storage.
La gestion academica corresponde a SIU Guarani. Todavia no se implementan modulos
de contenido, Docker, OCI, refresh tokens ni microservicios.

## Requisitos y versiones

- Java 21 (JDK).
- Spring Boot 3.5.16, con dependencias de Spring Security, Hibernate, PostgreSQL y
  Flyway administradas por su BOM.
- Maven 3.9.16 mediante Maven Wrapper incluido; no hace falta instalar Maven.
- SpringDoc OpenAPI 2.9.1, linea compatible con Spring Boot 3.
- Una base PostgreSQL para ejecutar la aplicacion. Los tests usan PostgreSQL 17.11
  temporal mediante Zonky Embedded Postgres 2.2.2, sin Docker y sin tocar tu base.

Referencias: [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[SpringDoc v2](https://springdoc.org/v2/),
[JWT en Spring Security](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html),
[PostgreSQL para tests](https://github.com/zonkyio/embedded-postgres).

Verificar `java -version` y `JAVA_HOME` antes de compilar. En PowerShell usar
`.\mvnw.cmd`; en Linux/macOS, `./mvnw` (o `sh mvnw` si falta permiso de ejecucion).
La primera ejecucion necesita Internet para descargar Maven y dependencias.

## PostgreSQL

Crear un usuario y una base dedicada desde `psql`, conectado con un administrador:

```sql
CREATE ROLE ifts2_app LOGIN;
\password ifts2_app
CREATE DATABASE ifts2 OWNER ifts2_app;
```

`\password` solicita la clave de forma interactiva. La aplicacion necesita permisos
para crear y modificar su esquema porque Flyway ejecuta las migraciones al iniciar.
No necesita un usuario superadministrador de PostgreSQL.

En Supabase usar una conexion JDBC PostgreSQL al proyecto, con acceso directo o un
pooler en modo sesion que permita las migraciones. Configurar TLS segun el entorno;
para verificar certificado y hostname usar `sslmode=verify-full` y el certificado
raiz correspondiente. La persistencia usa PostgreSQL estandar; no utiliza Supabase
Auth. La integracion HTTP de archivos esta aislada en el modulo de storage.

Flyway aplica `src/main/resources/db/migration/V1__crear_usuarios.sql`. Hibernate usa
`ddl-auto=validate`: comprueba el esquema pero no lo crea ni modifica. Para cambios
posteriores agregar migraciones `V2__...sql`, sin editar las ya aplicadas.

## Configuracion

| Variable | Obligatoria | Valor predeterminado / significado |
| --- | --- | --- |
| `DB_URL` | Si | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/ifts2` |
| `DB_USERNAME` | Si | Usuario PostgreSQL |
| `DB_PASSWORD` | Si | Password PostgreSQL |
| `JWT_SECRET` | Si | Base64 de al menos 32 bytes aleatorios |
| `JWT_ISSUER` | No | `ifts2-backend` |
| `JWT_ACCESS_TOKEN_TTL` | No | `15m`; minimo `1s`, maximo `1h` |
| `PORT` | No | `8080` |
| `API_DOCS_ENABLED` | No | `true`; habilita OpenAPI y Swagger UI |
| `BOOTSTRAP_ADMIN_ENABLED` | No | `false`; habilita la carga inicial del ADMIN |
| `BOOTSTRAP_ADMIN_NOMBRE` | Solo para carga inicial | Nombre, hasta 100 caracteres |
| `BOOTSTRAP_ADMIN_APELLIDO` | Solo para carga inicial | Apellido, hasta 100 caracteres |
| `BOOTSTRAP_ADMIN_EMAIL` | Solo para carga inicial | Email valido, hasta 254 caracteres |
| `BOOTSTRAP_ADMIN_PASSWORD` | Solo para carga inicial | Minimo 12 caracteres, maximo 72 bytes UTF-8 |

Configuracion de storage:

| Variable | Predeterminado | Significado |
| --- | --- | --- |
| `STORAGE_PROVIDER` | `none` | `none` o `supabase`; sin proveedor, usuarios y autenticacion siguen disponibles |
| `STORAGE_TEST_ENDPOINTS_ENABLED` | `false` | Habilita los endpoints TEMPORALES; requiere proveedor configurado |
| `SUPABASE_URL` | Sin valor | URL HTTPS raiz del proyecto; obligatoria con `supabase` |
| `SUPABASE_STORAGE_BUCKET` | Sin valor | Nombre del bucket publico; obligatorio con `supabase` |
| `SUPABASE_SERVICE_ROLE_KEY` | Sin valor | Credencial privilegiada `service_role`, exclusivamente de backend |
| `STORAGE_MAX_IMAGE_SIZE` | `5MB` | Limite de JPEG, PNG y WEBP |
| `STORAGE_MAX_DOCUMENT_SIZE` | `10MB` | Limite de PDF |
| `STORAGE_MAX_FILE_SIZE` | `10MB` | Limite de archivo multipart del servidor |
| `STORAGE_MAX_REQUEST_SIZE` | `11MB` | Limite total de request multipart, incluido su encabezado |
| `STORAGE_CONNECT_TIMEOUT` | `5s` | Timeout de conexion HTTP al proveedor |
| `STORAGE_READ_TIMEOUT` | `15s` | Timeout de lectura HTTP del proveedor |

Los limites de imagen/documento admiten entre 1 byte y 100 MB; los timeouts, entre
1 segundo y 2 minutos. Mantener el limite multipart al menos tan grande como el
mayor limite de archivo y reservar espacio adicional para el request completo.
El bucket puede imponer un limite adicional mas estricto. Si storage esta
deshabilitado, no se requieren variables de Supabase ni se crea un cliente HTTP.

No hay secretos predeterminados. Spring Boot no carga archivos `.env` automaticamente:
exportar variables en la terminal, configurarlas en el IDE o usar el gestor de
secretos del entorno. `.env`, claves privadas, archivos locales y generados se
excluyen en `.gitignore`.

Ejemplo de configuracion en PowerShell 7, con entrada de passwords sin mostrarlas:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ifts2'
$env:DB_USERNAME = 'ifts2_app'
$env:DB_PASSWORD = Read-Host 'Password PostgreSQL' -MaskInput

$jwtBytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Conservar `JWT_SECRET` en la configuracion segura del entorno para los siguientes
arranques. Generarlo de nuevo invalida todos los tokens emitidos con la clave anterior.
Todas las instancias de un mismo ambiente deben compartir clave e issuer.

Si se utiliza el JDK portable de esta carpeta de trabajo, configurar esta terminal:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools -Directory -Filter 'jdk-21*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

`.tools` es una herramienta local excluida de Git; en otros equipos instalar Java 21
y configurar `JAVA_HOME` con su ubicacion.

## Primer administrador

Para crear la primera cuenta administrativa se incluye una carga inicial opcional.
Solo crea un ADMIN si la tabla `usuarios` esta vacia;
si ya existe cualquier usuario, no crea ni modifica cuentas.

Configurar ademas, solo para el primer arranque:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_NOMBRE = Read-Host 'Nombre'
$env:BOOTSTRAP_ADMIN_APELLIDO = Read-Host 'Apellido'
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Email'
$env:BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'Password inicial' -MaskInput
.\mvnw.cmd spring-boot:run
```

La password se codifica con el mismo `PasswordEncoder` BCrypt que usa el login.
Ejecutar esta carga con una sola instancia sobre la base vacia. Despues de crear
el ADMIN, detener la aplicacion, quitar las variables y arrancar normalmente:

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_ENABLED, Env:BOOTSTRAP_ADMIN_NOMBRE, Env:BOOTSTRAP_ADMIN_APELLIDO, Env:BOOTSTRAP_ADMIN_EMAIL, Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd spring-boot:run
```

No se incluyen usuarios ni passwords de ejemplo en las migraciones. A partir de
esa primera cuenta, los ADMIN pueden gestionar usuarios mediante la API.

## Ejecutar

Con las variables de base y JWT disponibles en la terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

API: `http://localhost:8080`. Si el puerto esta ocupado, establecer `PORT` a otro.
Para ejecutar el artefacto compilado:

```powershell
.\mvnw.cmd clean verify
java -jar target/ifts2-backend-0.0.1-SNAPSHOT.jar
```

En equipos con Maven instalado tambien se puede usar `mvn clean verify`.

## Endpoints

| Metodo | Ruta | Acceso |
| --- | --- | --- |
| GET | `/api/health` | Publico, devuelve `{"status":"UP"}` |
| POST | `/api/auth/login` | Publico, valida email y password |
| GET | `/api/admin/test` | Temporal, ADMIN o EDITOR |
| GET | `/api/admin/usuarios/test` | Temporal, solo ADMIN |
| GET | `/api/admin/usuarios?page=0&size=20` | ADMIN; listado paginado |
| GET | `/api/admin/usuarios/{id}` | ADMIN; detalle por UUID |
| POST | `/api/admin/usuarios` | ADMIN; crear usuario activo |
| PUT | `/api/admin/usuarios/{id}` | ADMIN; actualizar datos y estado |
| PUT | `/api/admin/usuarios/{id}/password` | ADMIN; restablecer password |
| POST | `/api/admin/storage/test` | Temporal y optativo; ADMIN o EDITOR; multipart |
| DELETE | `/api/admin/storage/test?objectKey=...` | Temporal y optativo; ADMIN o EDITOR |
| GET | `/api/admin/storage/test/url?objectKey=...` | Temporal y optativo; ADMIN o EDITOR |
| GET | `/v3/api-docs` | OpenAPI publico, deshabilitable |
| GET | `/swagger-ui/index.html` | Swagger UI publico, deshabilitable |

`/api/health` indica que la API responde; no es un chequeo de disponibilidad de
PostgreSQL. Las rutas no declaradas se deniegan por defecto. Todo `/api/admin/**`
requiere un token valido y rol permitido; `/api/admin/usuarios/**` y su ruta raiz
estan reservados a ADMIN, independientemente del metodo HTTP.

Login y prueba desde PowerShell:

```powershell
$email = Read-Host 'Email'
$password = Read-Host 'Password' -MaskInput
$body = @{ email = $email; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/test' -Headers @{ Authorization = "Bearer $($login.accessToken)" }
Remove-Variable password, body, login
```

La respuesta de login tiene `accessToken`, `tokenType` (`Bearer`) y `expiresIn`
(segundos). En Swagger UI ejecutar el login, abrir **Authorize** y pegar unicamente
el `accessToken`. Swagger agrega el prefijo `Bearer`. Los endpoints temporales
permiten comprobar ambos niveles de permisos y se podran quitar mas adelante.

OpenAPI agrupa los contratos en `Authentication`, `Users`, `Storage` y `System`.
Los endpoints de storage aparecen solo al habilitar las pruebas de storage.

## Administracion de usuarios

Crear un usuario requiere `nombre`, `apellido`, `email`, `password` y `rol`.
Nombre y apellido admiten hasta 100 caracteres; email, hasta 254. Se recortan los
espacios externos del email y se convierte a minusculas. La password requiere
al menos 12 caracteres y hasta 72 bytes UTF-8, con la misma politica que la carga
inicial. El resultado es `201 Created` con `Location` y un `UsuarioResponse`.

El listado incluye usuarios activos e inactivos. `page` empieza en cero; `size`
admite 1 a 100. La respuesta contiene `content`, `page`, `size`, `totalElements` y
`totalPages`, ordenada por `createdAt` descendente e `id` ascendente. Cada usuario
expone solamente `id`, `nombre`, `apellido`, `email`, `rol`, `activo`, `createdAt`
y `updatedAt`. Nunca devuelve credenciales ni entidades JPA.

Se eligio **PUT** para reemplazar el conjunto completo de datos administrables,
sin semantica ambigua entre campos ausentes y nulos. Requiere estos cinco campos:

```json
{
  "nombre": "Nombre",
  "apellido": "Apellido",
  "email": "usuario@ifts2.edu.ar",
  "rol": "EDITOR",
  "activo": true
}
```

`activo=false` desactiva y `activo=true` reactiva la cuenta. No existe DELETE de
usuarios ni eliminacion fisica. El cambio de password se realiza unicamente con
`PUT /api/admin/usuarios/{id}/password`, cuyo body contiene solo `password`; devuelve
`204 No Content`. Solo ADMIN puede usar estas operaciones, incluso para su propia
password. Los campos JSON desconocidos se rechazan con 400: no se acepta
`passwordHash`, ni `password` en el PUT de datos administrables.

No se permite desactivar ni degradar al ultimo ADMIN activo. Tampoco se permite
desactivar o degradar la propia cuenta, aunque exista otro ADMIN; debe realizarlo
otro administrador. La comprobacion bloquea las filas de ADMIN activos dentro de
la misma transaccion y en un orden estable. Esto evita que dos cambios concurrentes
eliminen ambos accesos administrativos. No requiere cambiar el esquema existente.

## Supabase Storage

La primera implementacion utiliza la API HTTP oficial con `RestClient` de Spring,
sin SDK adicional. La configuracion no crea proyectos ni buckets automaticamente.

1. En el proyecto de Supabase, crear un bucket de archivos, por ejemplo
   `ifts2-institucional`, desde **Storage**.
2. Marcar el bucket como **publico** para servir las imagenes y PDF institucionales.
   Todo objeto de ese bucket sera publicamente legible; no colocar archivos privados.
3. Configurar un limite de archivo acorde con la API (10 MB por defecto) y permitir
   `image/jpeg`, `image/png`, `image/webp` y `application/pdf`.
4. Obtener la clave de backend `service_role` de las API keys del proyecto.
   Esta implementacion usa la clave JWT `service_role`, no una clave `anon` o
   `publishable`. Guardarla en variables de entorno o en el gestor de secretos.
5. Mantener las escrituras restringidas. No crear politicas abiertas de INSERT,
   UPDATE o DELETE para visitantes. La clave de servicio usada por este backend
   evita RLS; la autorizacion ADMIN/EDITOR se aplica antes de llamar al proveedor.

La lectura publica del bucket no otorga permisos de escritura. El JWT de esta API
no se envia a Supabase. La credencial de servicio se usa solo en los headers
`apikey` y `Authorization` de la conexion backend-proveedor y no aparece en
respuestas, logs ni OpenAPI. No colocarla en una variable del frontend ni en
el campo **Authorize** de Swagger.

Configurar en PowerShell 7, junto con las variables de PostgreSQL y JWT:

```powershell
$env:STORAGE_PROVIDER = 'supabase'
$env:SUPABASE_URL = Read-Host 'URL HTTPS del proyecto Supabase'
$env:SUPABASE_STORAGE_BUCKET = 'ifts2-institucional'
$env:SUPABASE_SERVICE_ROLE_KEY = Read-Host 'Clave service_role' -MaskInput
$env:STORAGE_TEST_ENDPOINTS_ENABLED = 'true'
.\mvnw.cmd spring-boot:run
```

Para verificar desde Swagger:

1. Abrir `http://localhost:8080/swagger-ui/index.html` e iniciar sesion mediante
   `/api/auth/login` con un ADMIN o EDITOR.
2. Pegar el `accessToken` del backend en **Authorize**.
3. Ejecutar `POST /api/admin/storage/test` con **Try it out** y seleccionar un
   JPEG, PNG, WEBP o PDF en el campo `file`.
4. La respuesta `201` incluye `objectKey`, `contentType`, `size` y `publicUrl`.
   Usar la URL para comprobar la descarga y conservar `objectKey` como identidad.
5. Probar `GET /api/admin/storage/test/url` con esa clave. Esta operacion deriva
   la URL, sin comprobar si el objeto existe.
6. Ejecutar `DELETE /api/admin/storage/test` con la misma clave; devuelve `204`.
   Si no existe, devuelve `404`.
7. Deshabilitar `STORAGE_TEST_ENDPOINTS_ENABLED` y reiniciar al finalizar.

Estos endpoints son temporales y solo trabajan bajo `pruebas/`. No permiten
subir o borrar objetos de los futuros modulos del CMS. El nombre original se
ignora; las claves se generan como `pruebas/{uuid}.extension`. No se aceptan rutas
absolutas, `..`, barras invertidas, URLs ni segmentos codificados como identidad.

La validacion central comprueba MIME declarado, firma binaria, archivo no vacio,
tamano declarado y bytes efectivamente leidos. La lectura esta acotada por el
limite configurado. No es un antivirus ni un parser completo de PDF/imagenes.
Para archivos mas grandes, Supabase recomienda subidas reanudables; esta milestone
usa upload estandar con limites pequenos y sin reintentos automaticos.

Contrato HTTP usado por el adaptador:

| Operacion | API de Supabase |
| --- | --- |
| Upload | `POST /storage/v1/object/{bucket}/{objectKey}`, cuerpo binario y `x-upsert: false` |
| Delete | `DELETE /storage/v1/object/{bucket}`, JSON `prefixes` con la clave exacta |
| URL publica | `/storage/v1/object/public/{bucket}/{objectKey}` |

La respuesta de borrado se verifica por nombre de objeto. Un resultado vacio o
`NoSuchKey` produce 404; credenciales rechazadas, bucket ausente, fallos de red,
redirecciones y errores internos producen 502, sin reenviar el cuerpo del proveedor.
Un archivo demasiado grande produce 413 y un MIME no permitido, 415. Las conexiones
usan HTTPS, timeouts configurables y no siguen redirecciones.

Referencias oficiales consultadas:
[upload estandar](https://supabase.com/docs/guides/storage/uploads/standard-uploads),
[API REST y borrado](https://supabase.com/docs/reference/self-hosting-storage),
[acceso y service key](https://supabase.com/docs/guides/storage/security/access-control),
[URLs publicas](https://supabase.com/docs/guides/storage/serving/downloads) y
[codigos de error](https://supabase.com/docs/guides/storage/debugging/error-codes).

### Independencia del proveedor

`StorageService` define `upload`, `delete` y `resolvePublicUrl` usando tipos propios,
`InputStream` y `URI`. No contiene tipos, buckets ni credenciales de Supabase.
`SupabaseStorageService` implementa el contrato; la dependencia va del adaptador
hacia la interfaz. La seleccion se hace por `app.storage.provider`, sin plugins
dinamicos. `none` deja el storage deshabilitado; `supabase` activa la implementacion.

La identidad es una clave como `noticias/{uuid}.webp` o `documentos/{uuid}.pdf`;
esos namespaces estan soportados por el contrato sin implementar tales entidades.
No se almacenan archivos en PostgreSQL ni URLs completas como identidad persistente.
Cuando se agreguen modulos de contenido, sus entidades guardaran `objectKey` y
resolveran la URL a traves de `StorageService`.

Para migrar posteriormente a OCI bastara con implementar el mismo contrato,
configurar ese proveedor y transferir los objetos conservando sus claves. No
habra que modificar los services/controllers del CMS ni reemplazar URLs en sus
datos. La implementacion de OCI queda fuera de esta milestone.

## Seguridad y errores

- BCrypt con costo 12. El limite de 72 bytes UTF-8 se valida antes de BCrypt.
- JWT firmado con HS256; contiene solo `sub` (UUID), `role`, `iss`, `iat` y `exp`.
  Se verifican firma, algoritmo, issuer, vencimiento, identidad y rol admitido.
- API stateless, sin login por formulario, HTTP Basic ni cookies de autenticacion.
  CSRF se deshabilita porque las credenciales se envian exclusivamente en el header
  `Authorization`. Tokens por query string no se aceptan. CORS no se abre globalmente.
- Usuarios inexistentes, inactivos o con password incorrecta reciben el mismo 401.
  Se evalua un hash BCrypt ficticio si el email no existe para reducir diferencias
  de tiempo. La respuesta del login indica `Cache-Control: no-store`.
- Al autenticar un JWT se consulta el usuario actual: debe existir, estar activo y
  conservar el rol del token. Asi, una cuenta desactivada o degradada deja de poder
  usar tokens anteriores. Se conserva Resource Server, HS256, los mismos claims y
  el TTL; no se agregan filtros JWT propios ni sesiones. Esto agrega una consulta
  a PostgreSQL por peticion autenticada.
- El control es por estado actual, no por lista de revocacion: si se restaura ese
  estado/rol antes del vencimiento, un token anterior puede volver a ser aceptado.
  Restablecer la password no revoca JWT emitidos; vencen con su TTL (15 minutos
  por defecto). No hay refresh tokens ni logout remoto en esta milestone.
- Para un despliegue publico usar HTTPS y configurar limitacion de intentos de
  login en el proxy o gateway. No se implementa un limitador distribuido aqui.
- Las excepciones MVC y de seguridad tienen el mismo DTO de error. No se devuelven
  SQL, valores rechazados, detalles internos ni trazas. Los errores inesperados
  registran solo el tipo de excepcion; no se registran cuerpos, passwords ni JWT.

Formato de error (los errores de validacion agregan objetos `field` y `message`):

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Credenciales invalidas",
  "path": "/api/auth/login",
  "errors": []
}
```

## Arquitectura y archivos

Monolito modular organizado por feature bajo `ar.edu.ifts2`:

```text
src/main/java/ar/edu/ifts2/
  Ifts2Application.java
  auth/
    controller/AuthController.java
    dto/LoginRequest.java, LoginResponse.java
    service/AuthService.java
  usuario/
    controller/UsuarioController.java
    dto/CrearUsuarioRequest.java, ActualizarUsuarioRequest.java
    dto/CambiarPasswordRequest.java, UsuarioResponse.java
    entity/Usuario.java, Rol.java
    repository/UsuarioRepository.java
    service/UsuarioService.java
    service/BootstrapAdminInitializer.java, BootstrapAdminProperties.java
    validation/PasswordPolicy.java, ValidPassword.java
  security/
    SecurityConfig.java, SecurityErrorHandler.java
    JwtConfig.java, JwtProperties.java, JwtService.java
    UsuarioJwtAuthenticationConverter.java
  storage/
    StorageService.java, StorageException.java
    model/StoredFile.java
    config/StorageConfig.java, StorageProperties.java
    validation/FileValidator.java
    supabase/SupabaseStorageConfig.java, SupabaseStorageProperties.java
    supabase/SupabaseStorageService.java
    controller/StorageTestController.java
    dto/StorageUploadResponse.java, StorageUrlResponse.java
  config/OpenApiConfig.java
  shared/
    controller/ProbeController.java
    dto/StatusResponse.java, PageResponse.java
    error/ApiError.java, GlobalExceptionHandler.java, ApiErrorController.java
    error/ResourceNotFoundException.java, BusinessConflictException.java
src/main/resources/
  application.yml
  db/migration/V1__crear_usuarios.sql
src/test/java/ar/edu/ifts2/
  auth/AuthSecurityIntegrationTest.java
  security/JwtConfigTest.java
  usuario/UsuarioIntegrationTest.java
  usuario/service/BootstrapAdminInitializerTest.java
  shared/error/GlobalExceptionHandlerTest.java
  storage/FileValidatorTest.java, SupabaseStorageServiceTest.java
  storage/StorageEndpointIntegrationTest.java
  storage/supabase/SupabaseStorageConfigTest.java
  support/PostgresIntegrationTest.java
```

Controllers limitados a HTTP y DTOs; el servicio de autenticacion consulta el
repositorio y emite JWT. Spring Security Resource Server procesa y valida Bearer
tokens con Nimbus, sin un filtro JWT casero ni un servidor OAuth externo.
La inyeccion es por constructor, no se usa Lombok y las entidades no se exponen.

La tabla `usuarios` usa UUID, email normalizado y unico, rol enumerado y timestamps
UTC. Las restricciones tambien estan en PostgreSQL. Los callbacks JPA mantienen
`createdAt` y `updatedAt`; las modificaciones manuales por SQL deben actualizar
`updated_at` explicitamente. No hay relaciones JPA nuevas ni cambios de esquema en
esta milestone. `AGENTS.md` se conserva localmente y ya no esta excluido de Git.

## Pruebas

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Los tests generan su clave JWT y passwords aleatorias en memoria. Levantan un
PostgreSQL real aislado en un puerto libre, aplican Flyway y validan el esquema
con Hibernate. No requieren variables de produccion, PostgreSQL instalado ni Docker.
Los binarios se descargan como dependencias Maven; necesitan permiso para ejecutar
procesos y abrir un puerto local. Ejecutar como usuario normal, no como root en Linux.
En Windows puede requerirse el runtime de Visual C++ indicado por Zonky.

Se verifican login correcto e incorrecto, usuario inexistente/inactivo, health
publico, 401 sin JWT, acceso ADMIN/EDITOR, 403 de EDITOR en usuarios, JWT alterado,
vencido o con claims invalidos, Swagger, validaciones, manejo de errores, claves
debiles, carga inicial segura, email unico, BCrypt y timestamps de persistencia.

La suite ampliada verifica paginacion, altas y cambios de usuarios, campos
prohibidos, desactivacion/reactivacion, permisos vigentes de JWT anteriores, reset
de password y dos degradaciones de ADMIN concurrentes contra PostgreSQL real.
Storage se prueba con `MockRestServiceServer` y un `StorageService` simulado para
los endpoints; nunca se llama al proyecto real de Supabase ni se requieren sus
credenciales. Se cubren headers HTTP, MIME/firma, tamanos, path traversal, claves
UUID, traduccion de errores y contratos OpenAPI con Bearer y multipart.
