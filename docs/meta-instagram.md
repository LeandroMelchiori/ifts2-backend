# Meta / Instagram: contrato para frontend y operacion

Integracion de lectura para la cuenta institucional usando **Facebook Login +
Fan Page**, confirmado por el equipo. No publica ni elimina contenido en Meta.
Usa el JWT del CMS para administracion; el token de Meta es una credencial distinta,
exclusiva del servidor. No hay SDK de Meta ni dependencias nuevas.

## Configuracion

| Variable | Default | Uso |
| --- | --- | --- |
| `META_ENABLED` | `false` | Habilita consultas externas a Graph |
| `META_API_VERSION` | vacio | Version explicita `vN.N` habilitada para la app Meta |
| `META_ACCOUNT_ID` | vacio | ID numerico de la cuenta profesional Instagram, no de la Fan Page |
| `META_ACCESS_TOKEN` | vacio | Credencial de Facebook Login con acceso a esa cuenta |
| `META_MAX_POSTS` | `25` | Maximo por sincronizacion, 1..100 |
| `META_CONNECT_TIMEOUT` | `5s` | Timeout de conexion, 1..30 segundos |
| `META_READ_TIMEOUT` | `15s` | Timeout HTTP, 1..60 segundos |
| `STORAGE_USAGE_LIMIT_BYTES` | `0` | Presupuesto del bucket; cero significa desconocido |

Con `META_ENABLED=true` se validan version, cuenta y credencial al arrancar.
No se elige implicitamente una version de Graph: confirmar la de la app antes
de activar. Los tests usan una version fija como fixture, no consultan Meta.

Para copiar previews se necesita un `StorageService` real. Con Supabase, reutilizar
`STORAGE_PROVIDER=supabase`, `SUPABASE_URL`, `SUPABASE_STORAGE_BUCKET` y
`SUPABASE_SERVICE_ROLE_KEY` descriptos en README. La clave service-role nunca va
al navegador. El bucket debe admitir JPEG/PNG/WEBP y el limite de imagenes del CMS.
No crear permisos anonimos de escritura ni duplicar estas tablas con Supabase CLI.
Flyway aplica V6 junto a las migraciones existentes.

Configurar secretos en las variables del servicio de despliegue. No pegarlos en
Git, capturas, URLs, tickets, logs ni archivos de frontend. Un token en una variable
`VITE_*` se entrega al navegador; retirar ese mecanismo de demo al conectar el CMS
y rotar cualquier credencial institucional que se haya distribuido de esa manera.

### Ciclo de la credencial

La emision/rotacion de la credencial de Facebook Login queda como operacion externa
del administrador de la app Meta. Este backend NO implementa OAuth interactivo ni
recibe tokens desde el frontend. El token vive solo en variables de entorno del
servidor (`META_ACCESS_TOKEN` en Render).

#### Intercambio manual de un User token

1. En [Meta for Developers](https://developers.facebook.com/) abrí la app y el
   **Graph API Explorer**.
2. Generá un **User token** corto con permisos tipicos:
   `pages_show_list`, `pages_read_engagement`, `instagram_basic`
   (ajustá a lo que Meta muestre vigente para Instagram Graph).
3. El script `scripts/exchange-meta-token.ps1`, separado del PR #1 de Carlos,
   intercambia un **User token de Facebook Login**. No usar un Page token ni un
   token de Instagram Login como entrada. Confirmar la version habilitada para
   la app; el script no elige una por defecto. App ID y App Secret corresponden
   a esa misma app, no necesariamente a las credenciales de Instagram Login.

En un equipo privado con PowerShell 7, desde la raiz del repositorio:

```powershell
$env:META_APP_ID = Read-Host 'App ID de Facebook Login'
$env:META_API_VERSION = Read-Host 'Version Graph habilitada (vN.N)'
$env:META_APP_SECRET = Read-Host 'App Secret' -MaskInput
$env:META_SHORT_TOKEN = Read-Host 'User token corto' -MaskInput
try {
    .\scripts\exchange-meta-token.ps1 -CopyToClipboard
} finally {
    Remove-Item Env:META_APP_SECRET, Env:META_SHORT_TOKEN -ErrorAction SilentlyContinue
}
```

El script no imprime tokens ni respuestas de error del proveedor. Requiere
`-CopyToClipboard` y copia la credencial al portapapeles local. Desactivar historial
y sincronizacion del portapapeles antes de usarlo; pegar en Render y limpiarlo con
`Set-Clipboard -Value ''`. No ejecutarlo desde CI, una sesion grabada o compartida.
El proceso necesita los secretos en memoria; no es un almacen de credenciales.

La duracion se obtiene de `expires_in` cuando esta presente; no asumir 60 dias
exactos ni una renovacion automatica. Confirmar vigencia, permisos y acceso a la
cuenta institucional en las herramientas de Meta. Esto no obtiene un Page token.

4. En Render → servicio `ifts2-backend` → Environment, seteá:

| Variable | Valor |
| --- | --- |
| `META_ENABLED` | `true` |
| `META_API_VERSION` | Version `vN.N` habilitada para la app, la misma usada en el intercambio |
| `META_ACCOUNT_ID` | ID numerico de la cuenta IG profesional |
| `META_ACCESS_TOKEN` | el `access_token` long-lived del paso 3 |

5. Guardá y redeployá / reiniciá el servicio.

No pegues el token en el chat, PRs ni `.env` del front (`VITE_*`). Si alguna vez
estuvo en el navegador, **rotá** el token en Meta y cargá solo el nuevo en Render.

Antes de produccion, el responsable de Meta debe confirmar tipo de token,
permisos, vencimiento y procedimiento de renovacion. No equiparar la vigencia de
User tokens con Page tokens ni prometer que estos ultimos nunca se invalidan.
Un error Graph 190 devuelve 503 controlado; el feed persistido sigue disponible.

El helper es una herramienta local: no agrega variables obligatorias al backend,
no cambia Render por si mismo y no agrega refresh automatico a Spring Boot.
Antes de usarlo con una cuenta real, revisar las guias oficiales de
[tokens de Facebook Login](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived/)
y [Instagram con Facebook Login](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/get-started/).
Los tests del helper simulan Meta y el portapapeles; queda pendiente validar el
intercambio real con el responsable de la app. Ejecutar los tests locales con:

```powershell
pwsh -NoProfile -File scripts/tests/Exchange-MetaToken.Tests.ps1
```

## Endpoints

Las rutas Meta admiten ADMIN o EDITOR con Ver (GET/HEAD) o Gestionar (mutaciones) en instagram. Ver [permisos](cuenta-y-permisos.md). Gestion de usuarios sigue
reservada a ADMIN. JWT ausente/invalido: 401; rol no autorizado: 403.

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/admin/meta/sync` | Importar/actualizar publicaciones recientes |
| GET | `/api/admin/meta/posts` | Lista administrativa paginada, filtro `visible` opcional |
| PUT | `/api/admin/meta/posts/{id}/visibilidad` | Publicar u ocultar |
| DELETE | `/api/admin/meta/posts/{id}/storage` | Liberar copia de preview |
| GET | `/api/meta/posts` | Feed publico de publicaciones visibles |
| GET | `/api/admin/storage/usage` | Medicion del bucket |

Los IDs Meta son strings numericos de hasta 40 digitos, NO UUID ni `ig:{id}`.
Paginacion: `page=0..1000000`, `size=1..100`, default 20. Orden de posts: fecha de
publicacion descendente e id ascendente. Se usa `PageResponse`:

```json
{"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
```

El feed social es independiente de `/api/galeria`, que conserva su contrato de
fotos de eventos. El frontend puede mostrar ambos feeds en secciones separadas.
No se interpreta el texto de captions como HTML.

### Sincronizacion

`POST /api/admin/meta/sync` no requiere body:

```json
{
  "sincronizados": 1,
  "message": "Sincronizacion completada",
  "posts": [{
    "id": "17890000000000000",
    "texto": "Actividad institucional",
    "imagenUrl": null,
    "thumbnailUrl": null,
    "mediaType": "IMAGE",
    "fechaPublicacion": "2026-09-20T12:00:00Z",
    "linkOriginal": "https://www.instagram.com/p/ejemplo/",
    "visible": false,
    "tieneStorage": false,
    "objectKey": null
  }]
}
```

- `sincronizados` cuenta los posts procesados, tanto nuevos como existentes.
- Los nuevos quedan ocultos para curacion editorial; no se descarga media aun.
- Upsert por ID; preserva visibilidad, copia y seleccion del carrusel.
- No elimina posts ausentes de la respuesta: importar los recientes no es una
  reconciliacion completa ni detecta automaticamente eliminaciones en Instagram.
- Usa cursores con hasta 10 paginas y `META_MAX_POSTS`; no sigue `paging.next`.
- Primero valida la respuesta externa y luego guarda el lote en una transaccion.
- No hay cron ni sincronizacion al consultar el sitio publico.

### Visibilidad y previews

```http
PUT /api/admin/meta/posts/17890000000000000/visibilidad
Content-Type: application/json
Authorization: Bearer <JWT_CMS>

{"visible": true}
```

Para publicar sin copia se consulta nuevamente el post para obtener una URL fresca,
se descarga una preview, se valida MIME/firma/tamano y se sube mediante
`ArchivoStorageService`. Solo entonces se marca visible. Repetir `visible=true`
con copia existente no duplica archivos. Si la transaccion revierte, se solicita
la eliminacion compensatoria de la copia nueva.

Decision de esta version: **una imagen de preview por publicacion**. IMAGE usa su
imagen; VIDEO usa thumbnail; CAROUSEL_ALBUM usa la imagen/thumbnail del primer hijo.
No se descargan videos ni todos los hijos del album. El original completo se abre
con `linkOriginal`. Se conserva `mediaType` para que el frontend indique el tipo.

Solo se descargan URLs HTTPS de subdominios de `cdninstagram.com` o `fbcdn.net`,
sin credenciales, puertos alternativos ni redirecciones. El cliente CDN no lleva
Authorization. Lecturas HTTP acotadas, limite por defecto 5 MB y formatos existentes
JPEG/PNG/WEBP. La URL temporal no se persiste ni se devuelve al frontend.

Si falta preview: 409; MIME no soportado: 415; tamano excedido: 413; error externo:
502; integracion/storage no configurado: 503. No se publica parcialmente.
Las respuestas publicas omiten `objectKey`, `visible` y `tieneStorage`.

Ocultar conserva la copia y la seleccion administrativa, pero retira el post del
feed y carrusel publicos. Como el bucket es publico, esto NO revoca URLs conocidas
ni caches externas; no usar para material confidencial. Una sincronizacion no
reemplaza previews existentes. Para recopiarlas: ocultar, retirar de destacados,
liberar copia y publicar nuevamente.

### Liberar almacenamiento

`DELETE /api/admin/meta/posts/{id}/storage` exige post oculto y NO seleccionado en
el carrusel, incluso si esa seleccion no aparece publicamente. De lo contrario:
409. No modifica visibilidad, no borra el registro y nunca llama DELETE a Graph.

```json
{"liberado": true, "objectKey": "meta/uuid-generado.png", "message": "Storage liberado"}
```

El servidor confirma borrado o ausencia en Storage ANTES de informar exito.
Si falla el proveedor: 502 y se conserva la referencia para reintentar. Repetir
tras exito devuelve 200, `liberado=true` y `objectKey=null`.

SQL y Storage no son una transaccion distribuida. Si Storage borra y SQL falla,
el post sigue oculto y su referencia puede quedar desactualizada: reintentar purge
antes de republicarlo; objeto ya ausente se considera exito. Caidas del proceso o
fallos de compensacion pueden dejar huerfanos que requieren revision manual.

## Destacados mixtos

`PUT /api/admin/destacados` reemplaza la lista completa, ahora con tres referencias:

```json
{"items": [
  {"noticiaId": "00000000-0000-4000-8000-000000000001"},
  {"eventoId": "00000000-0000-4000-8000-000000000002"},
  {"metaPostId": "17890000000000000"}
]}
```

Exactamente una referencia por item, todos existentes/publicados o visibles,
sin duplicados y maximo SEIS globales. Mantiene orden atomico y campos anteriores;
agrega `metaPostId` y `tipo` (`NOTICIA`, `EVENTO`, `META`) a ambas respuestas.
Para META, titulo es un extracto del caption, resumen es el caption, portadaUrl es
la copia y enlaceUrl el permalink. No hay un segundo flag de destacado.

Ocultar un seleccionado mantiene su cupo pero lo quita del carrusel publico.
El bloqueo compartido del carrusel serializa sync, visibilidad, purge y seleccion
para evitar carreras. Publicar puede mantener ese bloqueo durante la descarga;
es una decision deliberada para este CMS de bajo volumen, con timeouts acotados.

## Medidor de almacenamiento

`GET /api/admin/storage/usage` lista metadatos reales mediante `StorageService`.
El adaptador Supabase recorre carpetas/paginas del bucket, hasta 100 requests de
100 entradas. Si el recorrido no puede completarse: 503, nunca una suma parcial.
Errores de proveedor/metadata: 502. Sin storage: 503, no un cero ficticio.

Devuelve `usedBytes`, `limitBytes`, `scope="BUCKET"`, `measuredAt` y `breakdown`
con `key`, `label`, `bytes`, `files`. Categorias: meta, noticias, eventos, galeria,
documentos y otros. Las fotos de eventos se clasifican por su asociacion SQL
porque comparten prefijo con portadas. Los huerfanos tambien cuentan.

`limitBytes` es null hasta configurar un presupuesto; el frontend debe mostrar
bytes sin porcentaje en ese caso. NO es la cuota total contratada en Supabase:
no incluye otros buckets, egress ni base de datos. Tampoco bloquea uploads por
presupuesto. El barrido no es una instantanea atomica ante cambios concurrentes.

## CORS y cambios del frontend

`CORS_ALLOWED_ORIGINS` admite lista explicita separada por comas. Defaults:
`http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://localhost:3000`.
Al configurar la variable se reemplaza la lista, no se agregan entradas.
Headers permitidos: Authorization, Content-Type, Accept. Sin cookies/credentials;
preflight de login/admin esta integrado con Spring Security. Agregar solo los
dominios reales de produccion/preview cuando se conozcan, no `*.vercel.app`.

Para el companero de frontend:

1. Retirar token/consultas Graph del browser y demo en produccion.
2. Persistir el switch con PUT visibilidad y usar el DTO devuelto.
3. Leer el feed desde `/api/meta/posts`; no mostrar posts ocultos por mezclar seeds.
4. En destacados, enviar `metaPostId` numerico como string y dejar de fusionar
   estrellas de `localStorage`. Migrar seleccion manualmente, no silenciosamente.
5. Antes de purge, ocultar y retirar del carrusel. Respetar 409/502/503 sin simular
   exito local; despues actualizar posts, destacados y medidor.
6. Mostrar placeholder cuando un post admin no tenga copia. Publicar lo descarga.
7. Invalidar caches del feed y carrusel al cambiar visibilidad o seleccion.

## Verificacion y pendientes operativos

Tests HTTP de Graph/CDN/Storage usan mocks SOLO en tests; integracion usa PostgreSQL
temporal real y Flyway. Cubren permisos, filtros, contratos, migracion V5 a V6,
preservacion editorial, rollback, purge, paginacion, limites, CORS y concurrencia.

```powershell
.\mvnw.cmd -B -ntp "-Dtest=Meta*Test,StorageUsageServiceTest,SupabaseStorageServiceTest" test
.\mvnw.cmd -B -ntp clean verify
```

Pendiente fuera de las pruebas locales: configurar cuenta/app/token/version reales,
verificar permisos, acordar operacion de rotacion y probar un ciclo real con el
bucket institucional. No se ejecuto migracion ni carga/borrado contra el proyecto
Supabase o la cuenta Meta del usuario durante esta implementacion.

Referencias de contrato: [coleccion oficial de Meta](https://www.postman.com/meta/instagram/collection/6yqw8pt/instagram-api),
[campos IGMedia del SDK oficial](https://github.com/facebook/facebook-nodejs-business-sdk/blob/main/src/objects/ig-media.js),
[listado de objetos de Supabase](https://github.com/supabase/storage/blob/master/src/http/routes/object/listObjects.ts).
