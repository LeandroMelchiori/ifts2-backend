# Mi cuenta y permisos del panel

El backend es la fuente de verdad. El frontend debe reemplazar `ifts2-admin-roles`
de localStorage por estas operaciones. No se importan permisos del navegador:
solo un ADMIN autenticado puede asignarlos. No hay nuevas variables de entorno.

## Cuentas y asignaciones

Los roles de API siguen siendo `ADMIN` y `EDITOR`. Cualquier ADMIN activo tiene
acceso total, sin depender de un email especial ni de una fila de panel_role.
Un EDITOR sin asignacion puede iniciar sesion, consultar Mi cuenta y cambiar su
email/password; recibe 403 en el CMS administrativo.

Cada asignacion pertenece a un usuario existente, activo y EDITOR (opcion A del
contrato del front). Primero crear el usuario mediante `/api/admin/usuarios`;
despues crear su asignacion. No se generan invitaciones ni passwords temporales.
`name` es el nombre visible de esa asignacion, no una plantilla compartida.

La relacion persistida usa `usuario_id` UNIQUE FK, no una copia del email. Las
respuestas exponen el email actual del usuario. Cambiar el email desde Mi cuenta
o administracion de usuarios conserva los permisos. Cambiar el email mediante
PUT panel-roles **reasigna** a otro EDITOR existente; no cambia su login ni el
del usuario anterior. Un usuario puede tener como maximo una asignacion.

Promover a ADMIN elimina su asignacion anterior, para evitar permisos latentes
si despues vuelve a ser EDITOR. Desactivar (`activo=false`) bloquea sus JWT y
conserva la asignacion; reactivarlo restaura ese acceso. Eliminar una asignacion
no borra ni desactiva al usuario.

## Mi cuenta

Todas estas operaciones requieren Bearer JWT y usan exclusivamente el `sub` para
identificar a la persona. No aceptan un ID ni un rol en el body.

| Metodo | Ruta | Body / respuesta |
| --- | --- | --- |
| GET | `/api/auth/me` | 200 `{id,nombre,apellido,email,rol,activo}` |
| PUT | `/api/auth/me/email` | `{email,currentPassword}`; 200 perfil actualizado |
| PUT | `/api/auth/me/password` | `{currentPassword,newPassword}`; 204 |
| POST | `/api/auth/verify-password` | `{password}`; 204 si coincide, 401 si no |
| GET | `/api/auth/me/panel-permissions` | 200 permisos efectivos |

Email se normaliza con trim y minusculas. Para mantener el contrato de Mi cuenta
del PR #1, email ocupado o password actual incorrecta en los cambios propios
devuelven **409**. Nueva password: minimo 12 caracteres y maximo 72 bytes UTF-8;
solo se guarda BCrypt. Datos invalidos devuelven 400, sin valores sensibles.

Verify-password no emite JWT ni crea una sesion privilegiada. Cada mutacion de
roles exige de nuevo la password. Email/password no revocan los JWT emitidos:
conservan su vencimiento (15 minutos por defecto). Desactivar una cuenta o cambiar
su rol API hace que un token incompatible sea rechazado en la proxima peticion.
No se implementan refresh tokens ni un sistema de revocacion por cambio de password.

## CRUD de asignaciones (solo ADMIN)

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/admin/panel-roles` | 200 array ordenado por name e id |
| POST | `/api/admin/panel-roles` | 201 item + Location |
| PUT | `/api/admin/panel-roles/{id}` | 200 item; reemplazo completo |
| DELETE | `/api/admin/panel-roles/{id}` | 204; elimina permisos en cascada |

POST y PUT usan:

```json
{
  "name": "Bedelia",
  "email": "bedelia@example.test",
  "permissions": {
    "informacion": {"view": true, "manage": true},
    "contenido": {"view": true, "manage": false}
  },
  "currentPassword": "<password del ADMIN autenticado>"
}
```

DELETE requiere body JSON `{ "currentPassword": "<password del ADMIN>" }`.
No poner passwords en query strings. POST/PUT/DELETE devuelven 401 si esa password
no coincide, 403 si quien llama no es ADMIN, 404 si usuario/asignacion no existe,
409 si ya hay asignacion o el destinatario es ADMIN/inactivo. Se serializan con
los cambios administrativos de usuarios para evitar asignaciones duplicadas o
cruces con promociones concurrentes.

Cada item contiene solo `{id,name,email,permissions}`. Las cuatro secciones siempre
estan presentes. Omitir una seccion en POST/PUT equivale a false/false; omitir todo
`permissions` es 400. `manage=true` requiere `view=true`; claves desconocidas,
valores null y campos extra son 400. Las mismas restricciones se aplican en DB.

Permisos efectivos para un EDITOR:

```json
{
  "fullAccess": false,
  "roleName": "Bedelia",
  "permissions": {
    "destacados": {"view": false, "manage": false},
    "informacion": {"view": true, "manage": true},
    "contenido": {"view": true, "manage": false},
    "instagram": {"view": false, "manage": false}
  }
}
```

Sin asignacion: roleName=null y las cuatro secciones false/false. Para ADMIN:
fullAccess=true, roleName="Admin" y todos los permisos true.

## Autorizacion del CMS

| Seccion | Prefijos de API administrativa |
| --- | --- |
| destacados | `/api/admin/destacados` |
| informacion | `/api/admin/documentos`, `/institucion`, `/carreras`, `/autoridades`, `/enlaces` |
| contenido | `/api/admin/noticias`, `/eventos`, `/galeria` |
| instagram | `/api/admin/meta` |

Los nombres abreviados de la tabla tambien comienzan por `/api/admin`.
GET/HEAD requieren Ver; POST/PUT/PATCH/DELETE requieren Gestionar, incluidos
uploads, eliminacion de archivos, estados de publicacion y sincronizacion Meta.
Los endpoints publicos conservan su filtro de contenido publicado/visible.

Usuarios y panel-roles son exclusivos de ADMIN. El probe temporal de Storage
`/api/admin/storage/test/**` tambien pasa a ser exclusivo de ADMIN. La medicion
agregada `/api/admin/storage/usage` y `/api/admin/test` admiten ADMIN o EDITOR con
al menos un permiso Ver; el total del bucket no esta separado por seccion.
Un prefijo administrativo nuevo queda reservado a ADMIN hasta mapearlo.

El JWT conserva solo identificador, rol API y claims de vigencia. Los permisos
se consultan en DB en cada peticion, por lo que actualizar, reasignar o eliminar
un rol afecta al mismo JWT desde la siguiente solicitud. Una operacion ya
autorizada en curso no se cancela retroactivamente.

## Transicion y verificacion

V8 crea panel_role y panel_role_permission con RLS y sin grants para Data API.
No modifica V1-V7, no crea usuarios y **no asigna permisos a EDITOR existentes**.
Antes de desplegar, coordinar el cambio del front y la carga por un ADMIN:

1. Tener un ADMIN activo y respaldo verificado.
2. Aplicar las migraciones con el despliegue habitual; nunca `ddl-auto=update`.
3. Crear asignaciones mediante la API para los EDITOR que correspondan.
4. Conectar Mi cuenta y el editor de roles del front; no usar localStorage para autorizar.
5. Probar lectura, gestion y rechazo de secciones con cada perfil.

Esta implementacion no hace merge del PR #1 ni despliega Render. Sus endpoints
de cuenta se incorporaron respetando el contrato; revisar posibles conflictos
antes de integrar ese PR por separado. Meta no cambia.

Swagger incluye Bearer y los contratos. `PanelRolIntegrationTest` y
`OwnAccountIntegrationTest` usan JWT reales, BCrypt y PostgreSQL temporal;
prueban CRUD, concurrencia, validaciones, cambios de permisos y de credenciales.
Los respaldos incluyen ambas tablas y el ensayo opcional de recuperacion
verifica asignaciones, permisos, constraints y cierre de grants del destino.
Ver [Operacion segura](operacion-segura.md).
