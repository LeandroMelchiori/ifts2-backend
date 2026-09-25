# Alineacion con la maqueta administrativa

Esta guia describe la etapa V5: noticias por area, carrusel web, datos del sitio
y fotos de eventos. La ampliacion V6 incorpora Meta, previews, visibilidad y
destacados mixtos; ver [Meta / Instagram](meta-instagram.md) para esos contratos.

## Decisiones y compatibilidad

- Se mantiene login por email en `POST /api/auth/login`, no por el usuario de demo.
- ADMIN y EDITOR gestionan contenido; administracion de usuarios sigue siendo ADMIN.
- Noticias funciona como publicacion institucional por area; no se agrega otro
  CRUD de "contenido" duplicado. ALUMNOS es una audiencia, no gestion academica.
- Los registros nuevos de la maqueta usan UUID existentes de la API, no slugs.
- La pantalla historica de "Eventos / carga web" debe consumir `/api/eventos`.
  Su ruta visual `/admin/noticias` no determina el dominio del backend.
- El boton Borrar contenido debe ofrecer archivar, mediante el endpoint de estado.
  No hay borrado definitivo de noticias/eventos. Si la interfaz conserva la palabra
  "Borrar", debera aclarar que archiva; preferible llamarlo "Archivar".
- Las fotos si pueden eliminarse: quitar una foto no elimina el evento.
- Se conservan endpoints y campos previos. Las respuestas tienen campos adicionales.
- No se copian datos ni credenciales de demo. La integracion Meta es opcional y
  permanece deshabilitada hasta configurar las credenciales del servidor.
- El frontend aun debe adaptar sus requests/responses y reemplazar localStorage.
  Esta implementacion no modifica ni conecta el repositorio de la maqueta.

## Noticias por area

Crear/editar mantiene los endpoints `POST /api/admin/noticias` y
`PUT /api/admin/noticias/{id}`. Ejemplo:

```json
{
  "titulo": "Espacio alumnos",
  "resumen": "Informacion y recursos institucionales",
  "contenido": "Texto de la publicacion",
  "area": "ALUMNOS",
  "mostrarEnNovedades": true,
  "enlaceUrl": "https://example.org/recursos",
  "fecha": "2025-03-10"
}
```

Areas: GENERAL, ALUMNOS, DOCENTES, TUTORIA y EVENTOS. GENERAL conserva contenido
previo que no tenia clasificacion. Todas las publicaciones son texto plano;
el frontend debe escaparlo, nunca interpretarlo como HTML.

`titulo`, `resumen` y `contenido` siguen siendo obligatorios. Para compatibilidad,
omitir o enviar null en `area`, `mostrarEnNovedades` o `fecha` conserva el valor
en ediciones; al crear usa GENERAL, true y la fecha UTC actual.
`enlaceUrl` es opcional, HTTPS sin credenciales, hasta 2048 caracteres;
omitirlo o enviar null lo elimina al editar. No se descarga su contenido.

`fecha` es la fecha editorial visible de la publicacion. Es independiente de
`publicadaAt`, que sigue asignandose en el servidor al publicar. Una fecha futura
no programa la publicacion ni modifica permisos.

Filtros combinables en GET publico y administrativo:

- `/api/noticias?area=ALUMNOS`
- `/api/noticias?mostrarEnNovedades=true`
- `/api/noticias?area=TUTORIA&mostrarEnNovedades=false`
- `/api/admin/noticias?area=DOCENTES&estado=BORRADOR`

Sin filtro, la lista publica sigue incluyendo todas las noticias publicadas.
`mostrarEnNovedades=false` no hace privado el contenido: sigue accesible en su
area y por ID. Borradores/archivadas nunca aparecen en la API publica, incluso
con JWT. Paginacion: page 0..1000000 y size 1..100 (20 por defecto).
Se conserva el orden por publicadaAt descendente (createdAt en administracion).

La seleccion en el carrusel es independiente del filtro Novedades: cualquier
noticia o evento publicado puede seleccionarse. No se persiste un segundo
booleano de "destacado" que pueda contradecir la lista ordenada.

## Destacados web

| Metodo | Endpoint | Acceso |
| --- | --- | --- |
| GET | `/api/destacados` | Publico |
| GET | `/api/admin/destacados` | ADMIN/EDITOR |
| PUT | `/api/admin/destacados` | ADMIN/EDITOR |

PUT reemplaza atomicamente la lista completa. El orden del array establece
posiciones 1..6; no se mandan posiciones individuales:

```json
{
  "items": [
    { "noticiaId": "00000000-0000-4000-8000-000000000001" },
    { "eventoId": "00000000-0000-4000-8000-000000000002" }
  ]
}
```

Los UUID son ilustrativos: deben existir y estar publicados. Cada elemento
requiere exactamente un ID; V6 permite tambien `metaPostId` numerico como string
para posts Meta visibles. Lista vacia limpia el carrusel.
Maximo seis, sin duplicados. Siete elementos, null o referencia ambigua: 400.
Recurso inexistente: 404. Duplicado o contenido sin publicar: 409.
Si falla cualquier referencia, la lista anterior permanece completa.

Las respuestas contienen posicion, noticiaId/eventoId/metaPostId, tipo, titulo,
resumen, portadaUrl y enlaceUrl (noticias con enlace y posts Meta).
El administrador recibe ademas `disponible`. Para abrir el detalle, el frontend
usa el ID y el tipo de referencia; no se impone una ruta de frontend.

Al archivar/retirar un seleccionado se oculta del carrusel publico sin borrar
la seleccion. Conserva su lugar y ocupa un cupo; la respuesta publica puede tener
huecos en posiciones. Republicar lo hace reaparecer. Para guardar otra lista,
quitar de ella los elementos no publicados o publicarlos previamente.

Un bloqueo sobre el registro tecnico `carrusel` serializa reemplazos completos,
incluso cuando esta vacio. Si dos editores guardan simultaneamente, prevalece la
ultima lista guardada; no se mezclan ambas. No hay control de versiones de UI.
Titulos, imagenes y textos se leen del contenido original, sin duplicarlos.

V6 agrega posts Meta al mismo carrusel, sin cambiar los IDs ni contratos de
noticias/eventos; el limite de seis se comparte entre los tres origenes.

## Datos del sitio

Crear primero la institucion con `PUT /api/admin/institucion`.
Los GET actuales de Institucion incluyen los nuevos campos.

`PUT /api/admin/institucion/datos-sitio` guarda juntos contacto, mapa y perfiles:

```json
{
  "direccion": "Direccion institucional",
  "email": "contacto@example.org",
  "telefono": "Telefono institucional",
  "busquedaMapa": "Direccion completa, Ciudad, Argentina",
  "sitioOficialUrl": "https://example.org",
  "instagramUrl": "https://www.instagram.com/perfil_institucional/",
  "instagramVisible": true,
  "facebookUrl": null,
  "facebookVisible": false
}
```

direccion/email y ambos booleanos son obligatorios. Telefono, busquedaMapa y
URLs admiten null. Un perfil visible requiere URL valida. Limites: direccion
300, email 254, telefono 50, busquedaMapa 500 y URLs 2048 caracteres.
Todas las URLs deben ser HTTPS sin credenciales.

El endpoint conserva nombre, descripcion, horarios y estado. Si no existe
Institucion devuelve 404. Si ya esta publicada, los cambios son inmediatos.
El PUT institucional original conserva estos ajustes adicionales.

El GET publico devuelve null para las URLs de perfiles ocultos; el administrativo
las conserva para que puedan reactivarse. El estado de Institucion sigue
controlando la publicacion del conjunto completo.

busquedaMapa es texto, no HTML ni iframe. El frontend construye el mapa y su
vista previa escapando/codificando el valor. El backend no llama a Google Maps.
Guardar un perfil de Instagram no importa sus posts ni requiere un token de Meta.

El boton de maqueta "Restaurar contacto oficial" no tiene un endpoint de reset:
no hay un conjunto de datos oficiales aprobado para sembrar/restaurar. Se puede
volver a guardar el contacto verificado mediante el mismo PUT, sin defaults de demo.

## Fotos por evento y galeria

La portada existente es independiente de las fotos de galeria. Eliminar o
reemplazar una no afecta la otra. Las fotos pertenecen a un evento y heredan su
visibilidad; no tienen otro estado editorial independiente.

| Metodo | Endpoint | Resultado |
| --- | --- | --- |
| GET | `/api/admin/eventos/{eventoId}/fotos` | Lista administrativa ordenada |
| POST | `/api/admin/eventos/{eventoId}/fotos` | Agregar foto, 201 con Location |
| PUT | `/api/admin/eventos/{eventoId}/fotos/{fotoId}` | Editar etiqueta/orden |
| PUT | `/api/admin/eventos/{eventoId}/fotos/{fotoId}/archivo` | Reemplazar imagen |
| DELETE | `/api/admin/eventos/{eventoId}/fotos/{fotoId}` | Eliminar foto, 204 |
| GET | `/api/eventos/{eventoId}/fotos` | Fotos de evento publicado |
| GET | `/api/galeria` | Galeria publica paginada |
| GET | `/api/admin/galeria` | Galeria administrativa paginada |

POST multipart requiere dos partes:

- `file`: JPEG, PNG o WEBP.
- `datos`: JSON con Content-Type application/json.

```json
{ "etiqueta": "Preparacion", "orden": 0 }
```

Etiqueta: obligatoria, texto plano, hasta 200 caracteres. Orden: obligatorio,
entero 0..10000. Los empates se resuelven por UUID. PUT de metadata recibe este
mismo JSON; PUT de archivo recibe solo multipart file. No se acepta objectKey ni
URL del cliente. El nombre original no se usa como clave.

Se admiten hasta 50 fotos por evento (limite operativo de esta etapa); todas las
escrituras bloquean el evento, evitando superar el limite en cargas simultaneas.
Con 50 fotos, otro POST devuelve 409 antes de subir el archivo. Los controles de
MIME, firma y tamano reutilizan FileValidator (5 MB por imagen por defecto).
La aplicacion no recorta imagenes a 4:3: esa presentacion corresponde al frontend.

GET por evento devuelve una lista; su longitud permite mostrar el contador de
fotos de la maqueta. La galeria global acepta eventoId opcional; la administrativa
tambien estado. page/size tienen los mismos limites que Noticias.
Orden global: fechaInicio del evento descendente, eventoId, orden e id.
Una galeria global filtrada por ID inexistente devuelve pagina vacia; el GET
de fotos de un evento inexistente, borrador o archivado devuelve 404 publicamente.

Las respuestas incluyen id de foto, eventoId, eventoTitulo, fechaInicio, etiqueta,
orden e imagenUrl. Solo las administrativas incluyen objectKey, estado y auditoria.
Una foto consultada/modificada bajo otro evento devuelve 404.

La carga/reemplazo elimina el objeto nuevo ante rollback SQL; el anterior se
elimina solo despues de commit. Eliminar una foto borra el registro y solicita
la limpieza del archivo despues de commit. Fallos de limpieza quedan registrados
como `Limpieza pendiente de archivo` para revision manual, sin deshacer el commit.
No hay cola persistente de reintentos: caidas del proceso pueden dejar huerfanos.

Sin proveedor: lectura funciona con URLs null, metadata puede editarse, pero
cargar/reemplazar/eliminar archivos devuelve 503 sin perder asociaciones.
No se crean archivos ficticios en produccion.

Como en portadas, un bucket publico no protege archivos conocidos al archivar
el evento: se ocultan referencias en la API, no se revocan URLs ya compartidas.
No almacenar material confidencial bajo este modelo.

## Persistencia y pruebas

V5 agrega campos de noticias e institucion, el registro tecnico de bloqueo del
carrusel, destacados y evento_fotos. Conserva V1-V4 y su contenido. Las noticias
previas quedan en GENERAL, visibles en Novedades y con fecha editorial derivada
de publicadaAt o createdAt en UTC. No se cargan redes, destacados ni fotos de demo.

Las pruebas nuevas cubren filtros, compatibilidad, validaciones, permisos,
visibilidad, carrusel mixto/reordenamiento, concurrencia, limite de fotos, galeria,
rollback, errores de storage, modo sin proveedor, OpenAPI y migracion V4 a V5.

```powershell
.\mvnw.cmd -B -ntp "-Dtest=MaquetaIntegrationTest,MaquetaSinStorageIntegrationTest" test
.\mvnw.cmd -B -ntp clean verify
```

## Pendientes reales

- Conectar/adaptar el frontend a estos contratos, incluidos email, UUID y archivado.
- Adaptar el frontend al [contrato Meta](meta-instagram.md), retirando tokens y
  decisiones editoriales locales. Verificar cuenta, permisos y bucket reales.
- Aprobar datos institucionales reales y el comportamiento de restauracion.
- Configurar almacenamiento y PostgreSQL reales, CORS del frontend y despliegue.
