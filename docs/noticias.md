# Noticias

Modulo institucional bajo `ar.edu.ifts2.noticia`. Utiliza PostgreSQL, Flyway y los
roles/JWT existentes. No requiere nuevas dependencias ni variables de entorno.
La migracion `V2__crear_noticias.sql` se aplica al iniciar, preservando usuarios.

## Modelo y decisiones

- UUID como identificador, consistente con usuarios. No hay slug ni titulo unico.
- `titulo`: obligatorio, hasta 200 caracteres.
- `resumen`: obligatorio, hasta 500 caracteres.
- `contenido`: obligatorio, hasta 50000 caracteres, persistido como `TEXT`.
- `portadaObjectKey`: referencia opcional `noticias/{uuid}.jpg|png|webp`, unica por noticia.
- `portadaUrl`: URL derivada mediante `StorageService`; no se guarda en PostgreSQL.
- Los tres campos se recortan en sus extremos. Se preservan los saltos de linea.
- `estado`: `BORRADOR`, `PUBLICADA` o `ARCHIVADA`.
- `publicadaAt`: fecha UTC de la ultima entrada en PUBLICADA, asignada por el servidor.
- `createdAt` y `updatedAt`: auditoria tecnica UTC, mantenida por JPA.

El contenido es texto plano: el frontend debe renderizar titulo, resumen y cuerpo
como texto, con escape HTML, nunca con `innerHTML`. La API no interpreta ni
sanitiza HTML para un editor enriquecido. La portada es una imagen opcional y se
administra mediante endpoints independientes. Esta version no incluye otros
adjuntos, autores publicos, categorias, programacion ni historial de revisiones.

Las noticias se crean siempre como BORRADOR. Publicar es una operacion explicita
disponible para ADMIN y EDITOR. Ambos roles pueden administrar todas las noticias.
Se puede pasar entre cualquiera de los tres estados: retirar a BORRADOR permite
volver a editar fuera de la vista publica; ARCHIVADA conserva el contenido para
recuperarlo posteriormente. No hay eliminacion fisica ni DELETE de noticias;
el DELETE de portada solo desasocia la imagen.

Repetir el mismo estado es idempotente. Volver a PUBLICADA desde otro estado
actualiza `publicadaAt`; retirar o archivar conserva la fecha anterior. Editar
una noticia publicada mantiene su estado y hace visibles los cambios de inmediato.
Para revisar antes de mostrar los cambios, pasar primero a BORRADOR.

El servicio delimita las transacciones y convierte entidades en DTOs. Edicion y
cambio de estado toman un bloqueo de fila, siguiendo el patron de usuarios, para
evitar que operaciones concurrentes sobrescriban cambios de campos distintos.
No hay control de versiones de edicion: si dos personas reemplazan el contenido,
queda la ultima escritura. Los indices cubren los ordenes de listado y las
restricciones SQL protegen estados y campos obligatorios.

La migracion `V3__agregar_portada_noticias.sql` agrega la clave nullable, sin
modificar noticias existentes. Una restriccion limita las claves al namespace
`noticias/` y a imagenes; otra impide compartir una portada entre noticias.

## Contrato HTTP

| Metodo | Ruta | Acceso y resultado |
| --- | --- | --- |
| GET | `/api/noticias` | Publico, 200, pagina de resumenes publicados |
| GET | `/api/noticias/{id}` | Publico, 200, detalle completo publicado |
| GET | `/api/admin/noticias` | ADMIN/EDITOR, 200, pagina administrativa |
| GET | `/api/admin/noticias/{id}` | ADMIN/EDITOR, 200, detalle de cualquier estado |
| POST | `/api/admin/noticias` | ADMIN/EDITOR, 201, borrador y header Location |
| PUT | `/api/admin/noticias/{id}` | ADMIN/EDITOR, 200, reemplazo de contenido |
| PUT | `/api/admin/noticias/{id}/estado` | ADMIN/EDITOR, 200, cambio editorial |
| PUT | `/api/admin/noticias/{id}/portada` | ADMIN/EDITOR, multipart `file`, 200, nueva portada |
| DELETE | `/api/admin/noticias/{id}/portada` | ADMIN/EDITOR, 204, quitar portada |

Ambos listados usan `page=0` y `size=20` por defecto; `page >= 0` y `1 <= size <= 100`.
El administrativo admite `estado=BORRADOR`, `PUBLICADA` o `ARCHIVADA`; omitirlo
incluye todos. El listado publico filtra PUBLICADA en la consulta a la base,
independientemente de cualquier parametro `estado` enviado por el cliente.

El orden publico es `publicadaAt DESC, id ASC`; el administrativo es
`createdAt DESC, id ASC`. No se admite ordenamiento arbitrario. Las paginas
contienen `content`, `page`, `size`, `totalElements` y `totalPages`.
El resumen publico omite `contenido`, `estado` y los timestamps administrativos;
el detalle publico agrega `contenido`. Ambos incluyen `portadaUrl`, null si no hay
portada o storage esta deshabilitado. Los endpoints administrativos agregan
`portadaObjectKey`. Nunca se exponen entidades JPA. Los bodies JSON de contenido
no aceptan claves ni URLs de portadas: la asociacion solo se realiza al subir.

Errores con el formato comun de `ApiError`:

- 400: UUID, paginacion, estado o contenido invalidos; campos JSON desconocidos.
- 401: JWT ausente/invalido, usuario inactivo o rol que ya no coincide con el token.
- 403: falta de permisos, incluyendo intentos de escritura por las rutas publicas.
- 404: noticia inexistente; tambien borrador o archivada en la API publica,
  incluso si la consulta lleva un JWT administrativo.
- 413: imagen/request demasiado grande; 415: MIME no permitido.
- 502: fallo de storage durante la subida o resolucion de URL.
- 503: storage deshabilitado al subir o quitar una portada existente.

## Portadas

`PUT /api/admin/noticias/{id}/portada` acepta multipart con una parte `file`.
Devuelve `{ "objectKey": "noticias/{uuid}.png", "publicUrl": "..." }` con HTTP 200,
tanto en la primera subida como al reemplazar. ADMIN y EDITOR pueden realizar
la operacion en cualquier estado editorial. No se altera la publicacion.

Se reutiliza `FileValidator`: JPEG, PNG y WEBP, MIME y firma binaria coherentes,
archivo no vacio y limite de `STORAGE_MAX_IMAGE_SIZE` (5 MB por defecto). PDF, SVG
y GIF se rechazan. El nombre original nunca determina la clave. La validacion
es de cabecera/firma, no un decodificador completo de imagenes ni un antivirus.
La capa de Noticias valida antes de invocar al proveedor; el adaptador conserva
sus propias validaciones para los demas consumidores de `StorageService`.

Las operaciones bloquean la fila de la noticia como las ediciones existentes.
En cada reemplazo se sube una clave nueva sin sobrescribir objetos. Solo esa
clave se guarda en la base. El archivo anterior se elimina despues del commit;
si la transaccion revierte, se intenta eliminar el nuevo y conservar el anterior.
Esto contempla tambien fallos al confirmar la transaccion, no solo al guardar.

DELETE desasocia la portada y luego intenta borrar el objeto. Repetirlo cuando
no hay portada devuelve 204, incluso sin storage configurado. Un objeto que ya
no existe se considera eliminado. No acepta un objectKey externo para borrar.
Los cambios de texto, publicacion o archivado conservan la portada existente.

PostgreSQL y storage no comparten una transaccion atomica. Si falla el borrado
posterior al commit, la operacion HTTP sigue siendo exitosa y se registra
`Limpieza pendiente de archivo` con la clave y el tipo de fallo, sin mensajes
internos del proveedor. Revisar esas claves contra `noticias.portada_object_key`
antes de eliminarlas manualmente. No hay cola persistente ni reintentos automaticos.
Una caida del proceso entre upload y commit/limpieza, un resultado SQL incierto,
o una subida aceptada por el proveedor cuya respuesta se pierde pueden dejar
objetos huerfanos: antes de produccion verificar/reconciliar el bucket con la base.

Con `STORAGE_PROVIDER=none` la API de noticias sigue disponible y conserva las
claves existentes, devolviendo `portadaUrl=null`. Para uploads reales configurar
Supabase con las variables del README; no hace falta habilitar los endpoints
temporales de storage. Las URLs se derivan al leer, de modo que cambiar proveedor
no obliga a reescribir noticias si se conservan las claves al migrar archivos.

El bucket actual es publico. Borradores y archivadas no se exponen desde la API
publica, pero una URL de imagen conocida sigue siendo accesible directamente;
retirar una noticia no revoca esa URL ni las copias cacheadas. No usar las portadas
para contenido confidencial. Archivar conserva el archivo; quitar portada intenta
eliminarlo, con las limitaciones de limpieza indicadas anteriormente.

## Probar desde Swagger

Iniciar con las variables de PostgreSQL y JWT del README. `STORAGE_PROVIDER=none`
es suficiente. Abrir `/swagger-ui/index.html`, iniciar sesion con `/api/auth/login`
y usar el token en `Authorize`. Las operaciones estan agrupadas en `News` y
`News Administration`.

1. Crear con `POST /api/admin/noticias`:

```json
{
  "titulo": "Jornada de proyectos del IFTS N. 2",
  "resumen": "Presentacion institucional de proyectos de estudiantes.",
  "contenido": "La jornada se realizara en la sede del instituto.\nSe compartiran los proyectos desarrollados durante el ciclo lectivo."
}
```

2. Usar el `id` devuelto. `GET /api/noticias/{id}` todavia responde 404.
3. Publicar con `PUT /api/admin/noticias/{id}/estado`:

```json
{ "estado": "PUBLICADA" }
```

4. Consultar `/api/noticias` y `/api/noticias/{id}` sin token. La noticia es visible.
5. Actualizar con `PUT /api/admin/noticias/{id}` enviando los tres campos completos.
   No enviar estado, fechas, id, portadaObjectKey ni portadaUrl: se rechazan con 400.
6. Retirar con el endpoint de estado y `{ "estado": "BORRADOR" }`, o archivar
   con `{ "estado": "ARCHIVADA" }`. Ambos la ocultan del listado y detalle publicos.

Con storage real configurado, usar `PUT /api/admin/noticias/{id}/portada` en Swagger,
seleccionar una imagen en `file` y ejecutar. Comprobar `portadaUrl` en el detalle
administrativo o publicado. Subir otra imagen para reemplazarla y usar DELETE en
la misma ruta para quitarla. Estas pruebas reales quedan pendientes de configurar
el proyecto/bucket y las credenciales; los tests automatizados usan mocks.

## Pruebas

```powershell
.\mvnw.cmd -B -ntp -Dtest=NoticiaIntegrationTest test
.\mvnw.cmd -B -ntp -Dtest=NoticiaPortadaIntegrationTest test
.\mvnw.cmd -B -ntp clean verify
```

La suite usa PostgreSQL temporal, migraciones reales y JWT firmados para usuarios
ADMIN/EDITOR. Verifica visibilidad, transiciones, permisos, validaciones,
paginacion, persistencia, concurrencia y el contrato generado de OpenAPI.
No utiliza Supabase real ni credenciales de produccion.

Portadas agrega cobertura de ambos roles con JWT, formatos y tamanos, persistencia
de claves y URLs derivadas, storage deshabilitado, fallos antes/despues del upload,
rollback y fallo de commit, limpieza fallida, objetos ya ausentes y reemplazos
concurrentes. El proveedor simulado existe solo en tests.
