# IFTS N. 2 - Backend institucional

Base de la API REST del CMS institucional. Esta milestone incluye infraestructura,
usuarios para autenticacion, login JWT y autorizacion por roles. La gestion academica
corresponde a SIU Guarani. No se implementan modulos de contenido, storage ni Docker.

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
raiz correspondiente. No se utiliza Supabase Auth ni una API propietaria.

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

Para hacer utilizable el login sin agregar una API de alta de usuarios, se incluye
una carga inicial opcional. Solo crea un ADMIN si la tabla `usuarios` esta vacia;
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

No se incluyen usuarios ni passwords de ejemplo en las migraciones. La gestion
de usuarios por API queda para una milestone posterior.

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
- El JWT ya emitido conserva sus permisos hasta su vencimiento aunque cambien el
  rol o el estado del usuario. Esta milestone no incluye refresh, logout remoto
  ni revocacion inmediata. Desactivar una cuenta impide nuevos logins.
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
    entity/Usuario.java, Rol.java
    repository/UsuarioRepository.java
    service/BootstrapAdminInitializer.java, BootstrapAdminProperties.java
  security/
    SecurityConfig.java, SecurityErrorHandler.java
    JwtConfig.java, JwtProperties.java, JwtService.java
  config/OpenApiConfig.java
  shared/
    controller/ProbeController.java
    dto/StatusResponse.java
    error/ApiError.java, GlobalExceptionHandler.java, ApiErrorController.java
src/main/resources/
  application.yml
  db/migration/V1__crear_usuarios.sql
src/test/java/ar/edu/ifts2/
  auth/AuthSecurityIntegrationTest.java
  security/JwtConfigTest.java
  usuario/service/BootstrapAdminInitializerTest.java
  shared/error/GlobalExceptionHandlerTest.java
```

Controllers limitados a HTTP y DTOs; el servicio de autenticacion consulta el
repositorio y emite JWT. Spring Security Resource Server procesa y valida Bearer
tokens con Nimbus, sin un filtro JWT casero ni un servidor OAuth externo.
La inyeccion es por constructor, no se usa Lombok y las entidades no se exponen.

La tabla `usuarios` usa UUID, email normalizado y unico, rol enumerado y timestamps
UTC. Las restricciones tambien estan en PostgreSQL. Los callbacks JPA mantienen
`createdAt` y `updatedAt`; las modificaciones manuales por SQL deben actualizar
`updated_at` explicitamente. No hay relaciones ni abstracciones de almacenamiento.

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
