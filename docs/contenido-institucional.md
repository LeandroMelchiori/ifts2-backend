# Modulos institucionales

Primera version de Eventos, Carreras, Autoridades, Enlaces, Documentos e
Institucion. Se suma a Noticias, usuarios, autenticacion y storage existentes.
Todo se puede compilar y probar sin cuentas de OCI/Supabase. No se implementa
gestion academica, inscripciones, notas ni cursadas.

La ampliacion V5 suma fotos multiples por evento, galeria, destacados web y datos
del sitio. Sus endpoints y reglas estan en [Contrato de la maqueta](maqueta-cms.md);
las portadas siguen siendo independientes de las fotos de galeria.

## Arquitectura

Cada feature tiene entidades, DTOs, repositorio, servicio y controllers publicos
y administrativos. Se mantienen transacciones y reglas de negocio en services,
inyeccion por constructor, DTOs validados y errores comunes. No hay un controller
CRUD generico ni un modelo unico que mezcle dominios.

`ContenidoEditorial` es un `@MappedSuperclass` usado solo por los seis modulos
nuevos para UUID, estado y timestamps. Cada modulo tiene su propia tabla;
no existe tabla base ni herencia SQL. Noticias conserva su entidad y enum para
no cambiar el contrato anterior.

`ArchivoStorageService` concentra validacion y compensacion de archivos entre
storage y SQL; tambien lo usa Noticias. La interfaz `StorageService` sigue siendo
independiente del proveedor. No se agregan dependencias, secretos ni variables
de entorno.

Flyway aplica `V4__crear_contenido_institucional.sql` despues de V1-V3, sin modificar
las migraciones anteriores ni los datos existentes. Se verifico con una prueba
que aplica primero V1-V3, crea una noticia con portada y luego aplica V4.
La migracion nueva no inventa contenido, carreras, autoridades ni datos de contacto.

## Reglas comunes

- ADMIN y EDITOR administran todo el contenido. Usuarios sigue siendo exclusivo de ADMIN.
- Crear genera BORRADOR; publicar requiere una operacion explicita de estado.
- Estados: `BORRADOR`, `PUBLICADA`, `ARCHIVADA`.
- BORRADOR y ARCHIVADA no aparecen en consultas publicas. El detalle devuelve 404,
  incluso si se envia un JWT administrativo.
- Archivar no elimina fisicamente contenido ni archivos; se puede restaurar.
- Repetir estado es idempotente. Al volver a PUBLICADA se renueva `publicadaAt`.
- PUT requiere todos los campos obligatorios; los opcionales omitidos quedan null.
  Conserva estado y archivos. Editar contenido publicado lo cambia de inmediato.
- Estado, UUID, auditoria y claves de archivos no se aceptan en requests de contenido.
- Las fechas se serializan en UTC. Los textos son planos y se recortan en sus
  extremos; el frontend debe mostrarlos con escape HTML.
- Ediciones y cambios de estado/archivo bloquean la fila. Dos ediciones completas
  concurrentes conservan la ultima escritura; no hay historial ni control de versiones.

## Endpoints

Para cada coleccion `eventos`, `carreras`, `autoridades`, `enlaces` y `documentos`:

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/{coleccion}` | 200, pagina de contenido publicado |
| GET | `/api/{coleccion}/{id}` | 200, detalle publicado por UUID |
| GET | `/api/admin/{coleccion}` | 200, pagina administrativa, filtro opcional `estado` |
| GET | `/api/admin/{coleccion}/{id}` | 200, detalle en cualquier estado |
| POST | `/api/admin/{coleccion}` | 201, borrador; header Location |
| PUT | `/api/admin/{coleccion}/{id}` | 200, reemplazo de contenido |
| PUT | `/api/admin/{coleccion}/{id}/estado` | 200, publicar/retirar/archivar |

No existe DELETE de las entidades. La ruta de estado recibe:

```json
{ "estado": "PUBLICADA" }
```

Listados: `page=0`, `size=20`; `0 <= page <= 1000000`, `1 <= size <= 100`.
El limite de pagina evita offsets fuera de rango de JPA.
Respuesta: `content`, `page`, `size`, `totalElements`, `totalPages`.
El detalle y las listas publicas omiten estado, auditoria interna y objectKey.
Los DTOs administrativos incluyen esos campos. No se permite ordenar por campos
arbitrarios; siempre se agrega UUID como desempate.

Institucion es un unico registro:

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/institucion` | 200 si esta publicada, de lo contrario 404 |
| GET | `/api/admin/institucion` | 200, contenido existente; 404 si no se cargo |
| PUT | `/api/admin/institucion` | 200, crear borrador inicial o reemplazar contenido |
| PUT | `/api/admin/institucion/estado` | 200, cambiar estado del registro existente |

Una restriccion SQL impide dos registros institucionales. Si dos primeras
escrituras simultaneas intentan crearlo, una puede recibir 409 y debe reintentar
su PUT. Las actualizaciones de un registro existente se serializan por fila.

## Campos por modulo

Los siguientes JSON son ejemplos, no datos oficiales precargados.

### Eventos

| Campo | Regla |
| --- | --- |
| `titulo` | Obligatorio; hasta 200 caracteres |
| `resumen` | Obligatorio; hasta 500 caracteres |
| `descripcion` | Obligatorio; hasta 20000 caracteres |
| `fechaInicio` | Obligatorio; ISO 8601 con zona u offset |
| `fechaFin` | Obligatorio; ISO 8601 con zona u offset |
| `lugar` | Obligatorio; hasta 300 caracteres |

`fechaFin` debe ser posterior a `fechaInicio`; tambien lo verifica PostgreSQL.
Se permiten eventos pasados para el archivo institucional. Orden publico por
`fechaInicio` ascendente. Filtro opcional `desde` inclusivo sobre el inicio, por
ejemplo `/api/eventos?desde=2030-01-01T00:00:00Z`; sin filtro incluye pasados.
Portada opcional. No hay recurrencia, inscripciones ni gestion de asistentes.

```json
{
  "titulo": "Jornada institucional",
  "resumen": "Presentacion de proyectos",
  "descripcion": "Actividad abierta en la sede del instituto.",
  "fechaInicio": "2030-04-12T18:00:00-03:00",
  "fechaFin": "2030-04-12T20:00:00-03:00",
  "lugar": "Auditorio"
}
```

### Carreras

| Campo | Regla |
| --- | --- |
| `nombre` | Obligatorio; hasta 200 caracteres |
| `tituloOtorgado` | Obligatorio; hasta 200 caracteres |
| `descripcion` | Obligatorio; hasta 20000 caracteres |
| `duracion` | Obligatorio; hasta 100 caracteres |
| `modalidad` | Obligatorio; hasta 100 caracteres |
| `requisitosIngreso` | Opcional; hasta 10000 caracteres |
| `orden` | Obligatorio; entero de 0 a 10000 |

Solo informacion institucional de la oferta. `duracion` y `modalidad` son textos
administrables, no enums academicos. Orden publico por `orden`, `nombre`, UUID.
Imagen opcional. No representa alumnos, materias cursadas ni inscripciones.

```json
{
  "nombre": "Tecnicatura de ejemplo",
  "tituloOtorgado": "Titulo de ejemplo",
  "descripcion": "Descripcion institucional de la propuesta.",
  "duracion": "3 anos",
  "modalidad": "Presencial",
  "requisitosIngreso": "Consultar requisitos vigentes.",
  "orden": 0
}
```

### Autoridades

| Campo | Regla |
| --- | --- |
| `nombre` | Obligatorio; hasta 100 caracteres |
| `apellido` | Obligatorio; hasta 100 caracteres |
| `cargo` | Obligatorio; hasta 150 caracteres |
| `descripcion` | Opcional; hasta 2000 caracteres |
| `orden` | Obligatorio; entero de 0 a 10000 |

Datos de presentacion publica y cargo; sin cuentas o datos privados asociados.
Orden publico por `orden`, `apellido`, UUID. Foto opcional.

```json
{
  "nombre": "Nombre",
  "apellido": "Apellido",
  "cargo": "Cargo institucional",
  "descripcion": "Presentacion institucional.",
  "orden": 0
}
```

### Enlaces

| Campo | Regla |
| --- | --- |
| `titulo` | Obligatorio; hasta 150 caracteres |
| `descripcion` | Opcional; hasta 500 caracteres |
| `url` | Obligatorio; hasta 2048 caracteres; URL HTTPS absoluta sin credenciales |
| `categoria` | Obligatorio; hasta 100 caracteres |
| `orden` | Obligatorio; entero de 0 a 10000 |

La URL se valida con `java.net.URI`: HTTPS, host y puerto validos, sin credenciales.
Se permiten query y fragmentos; no se aceptan javascript, data, HTTP o rutas relativas.
El backend solo publica el enlace, no descarga su destino ni redirige peticiones.
Categoria en texto libre. Orden publico por `orden`, `titulo`, UUID.

```json
{
  "titulo": "Sistema externo",
  "descripcion": "Acceso al sistema institucional externo.",
  "url": "https://example.invalid/sistema",
  "categoria": "Sistemas",
  "orden": 0
}
```

### Documentos

| Campo | Regla |
| --- | --- |
| `titulo` | Obligatorio; hasta 200 caracteres |
| `descripcion` | Opcional; hasta 2000 caracteres |
| `tipo` | Obligatorio; valor del enum TipoDocumento |

Tipos: `PLAN_ESTUDIO`, `CORRELATIVIDADES`, `REGLAMENTO`, `CALENDARIO`, `HORARIO`,
`RESOLUCION`, `FORMULARIO`, `INSTRUCTIVO`, `OTRO`. Filtro publico opcional `tipo`.
Orden publico por ultima publicacion descendente. Una sola entidad para todos
los tipos, sin tablas por categoria. No hay asociacion a Carrera en esta version.

El PDF es obligatorio para publicar: sin archivo se devuelve 409. Para quitar
el PDF de un documento publicado, primero retirarlo a BORRADOR o archivarlo.
Reemplazar el PDF publicado esta permitido y conserva su estado. Una restriccion
SQL impide PUBLICADA sin objectKey. La API no comprueba en cada lectura que el
objeto remoto siga existiendo.

```json
{
  "titulo": "Reglamento de ejemplo",
  "descripcion": "Documento institucional de consulta.",
  "tipo": "REGLAMENTO"
}
```

### Institucion

| Campo | Regla |
| --- | --- |
| `nombre` | Obligatorio; hasta 200 caracteres |
| `descripcion` | Obligatorio; hasta 20000 caracteres |
| `direccion` | Obligatorio; hasta 300 caracteres |
| `email` | Obligatorio; hasta 254 caracteres; email valido |
| `telefono` | Opcional; hasta 50 caracteres |
| `horariosAtencion` | Opcional; hasta 500 caracteres |

El primer PUT crea el registro como BORRADOR. Los posteriores conservan UUID,
fecha de creacion y estado editorial. No se publica automaticamente informacion
de contacto ni se crean registros ficticios.

```json
{
  "nombre": "Nombre institucional",
  "descripcion": "Presentacion del instituto.",
  "direccion": "Direccion institucional",
  "email": "contacto@example.invalid",
  "telefono": null,
  "horariosAtencion": "Consultar horarios vigentes."
}
```

## Imagenes y PDF

| Archivo | PUT y DELETE administrativos | Namespace |
| --- | --- | --- |
| Portada de evento | `/api/admin/eventos/{id}/portada` | `eventos/` |
| Imagen de carrera | `/api/admin/carreras/{id}/imagen` | `carreras/` |
| Foto de autoridad | `/api/admin/autoridades/{id}/foto` | `autoridades/` |
| PDF institucional | `/api/admin/documentos/{id}/archivo` | `documentos/` |

PUT recibe multipart con parte `file` y devuelve 200 con `objectKey` y `publicUrl`.
DELETE devuelve 204 y no borra la entidad. No se pueden suministrar claves para
asociar o borrar archivos ajenos. Los nombres originales se ignoran y las claves
contienen UUID. Una clave no puede compartirse entre dos registros del mismo modulo.

Imagenes: JPEG, PNG o WEBP, con `STORAGE_MAX_IMAGE_SIZE` (5 MB por defecto).
Documentos: solo PDF con `STORAGE_MAX_DOCUMENT_SIZE` (10 MB por defecto).
Se verifica MIME, firma, tamano declarado y real; no es un antivirus ni un parser
completo del formato. Se mantienen tambien los limites multipart del servidor.

Se sube un objeto nuevo, se cambia la referencia dentro de la transaccion y solo
despues del commit se intenta borrar el anterior. Si SQL revierte, se intenta
borrar el nuevo. Fallos de limpieza se registran como `Limpieza pendiente de archivo`
con clave y tipo de error, sin detalles del proveedor. No hay cola ni reintentos
persistentes. Caidas del proceso o respuestas perdidas pueden dejar huerfanos:
reconciliar claves del bucket con las columnas objectKey antes de borrarlos.

Las URLs se derivan al leer; solo las claves se persisten. Con
`STORAGE_PROVIDER=none`, el contenido sigue funcionando y las URLs son null.
Las operaciones que necesitan storage devuelven 503; quitar un archivo que ya
no esta asociado devuelve 204. No se incluye un proveedor ficticio en produccion.

El bucket del adaptador actual es publico: ocultar contenido en la API no revoca
una URL de archivo conocida ni copias cacheadas. No cargar documentos privados.
Para cambiar a OCI debera implementarse el adaptador y migrar los objetos
conservando sus claves; esta etapa no conecta cuentas externas.

## Errores y Swagger

Se usa `ApiError` existente: 400 por request/UUID/filtro invalido, 401 por falta
de autenticacion, 403 por permisos, 404 por recurso inexistente/no publicado,
409 por reglas editoriales o conflicto de datos, 413 por tamano, 415 por MIME,
502 por fallo del proveedor y 503 por storage deshabilitado. Sin trazas ni secretos.

Swagger agrupa las lecturas y administracion por modulo. Las operaciones admin
indican Bearer JWT, campos, respuestas y multipart. Autenticarse con login y
Authorize; usar los JSON anteriores para crear, publicar, consultar y archivar.
Las imagenes/PDF reales requieren configurar storage; antes de eso sus pruebas
se ejecutan contra mocks en tests.

## Verificacion local

Con Java 21, Maven Wrapper y sin servicios externos:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=ContenidoIntegrationTest,ContenidoSinStorageIntegrationTest' test
.\mvnw.cmd -B -ntp clean verify
```

Las pruebas usan PostgreSQL temporal real y migraciones Flyway. Cubren ambos
roles, restricciones de publicacion, filtros, paginacion, URLs seguras, fechas,
instancia unica de Institucion, archivos por namespace, errores y compensacion,
modo sin proveedor, OpenAPI y upgrade desde V3. Las pruebas existentes de Noticias
siguen cubriendo concurrencia de portadas y fallos de commit del servicio compartido.

## Pendiente de integracion

- Configurar PostgreSQL del ambiente y su conexion segura, local o Supabase.
- Configurar bucket/credenciales reales y validar subida, reemplazo, borrado y URLs.
- Implementar OCI solo cuando se decida usarlo; la abstraccion ya esta disponible.
- Verificar copias de seguridad, limites operativos, limpieza de huerfanos y monitoreo
  antes de produccion.
- Cargar contenido institucional real y confirmar criterios editoriales con el instituto.
- Integrar el frontend y sus origenes CORS cuando esten definidos.

No se incluyeron Docker, despliegue, frontend, editor HTML, busqueda avanzada,
relaciones entre documentos/carreras, refresh tokens ni gestion academica.
