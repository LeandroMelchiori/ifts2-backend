# Noticias

Modulo institucional bajo `ar.edu.ifts2.noticia`. Utiliza PostgreSQL, Flyway y los
roles/JWT existentes. No requiere nuevas dependencias ni variables de entorno.
La migracion `V2__crear_noticias.sql` se aplica al iniciar, preservando usuarios.

## Modelo y decisiones

- UUID como identificador, consistente con usuarios. No hay slug ni titulo unico.
- `titulo`: obligatorio, hasta 200 caracteres.
- `resumen`: obligatorio, hasta 500 caracteres.
- `contenido`: obligatorio, hasta 50000 caracteres, persistido como `TEXT`.
- Los tres campos se recortan en sus extremos. Se preservan los saltos de linea.
- `estado`: `BORRADOR`, `PUBLICADA` o `ARCHIVADA`.
- `publicadaAt`: fecha UTC de la ultima entrada en PUBLICADA, asignada por el servidor.
- `createdAt` y `updatedAt`: auditoria tecnica UTC, mantenida por JPA.

El contenido es texto plano: el frontend debe renderizar titulo, resumen y cuerpo
como texto, con escape HTML, nunca con `innerHTML`. La API no interpreta ni
sanitiza HTML para un editor enriquecido. Esta version no incluye imagenes,
adjuntos, autores publicos, categorias, programacion de publicaciones ni historial
de revisiones. Storage conserva su contrato actual para una integracion posterior.

Las noticias se crean siempre como BORRADOR. Publicar es una operacion explicita
disponible para ADMIN y EDITOR. Ambos roles pueden administrar todas las noticias.
Se puede pasar entre cualquiera de los tres estados: retirar a BORRADOR permite
volver a editar fuera de la vista publica; ARCHIVADA conserva el contenido para
recuperarlo posteriormente. No hay eliminacion fisica ni endpoint DELETE.

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

Ambos listados usan `page=0` y `size=20` por defecto; `page >= 0` y `1 <= size <= 100`.
El administrativo admite `estado=BORRADOR`, `PUBLICADA` o `ARCHIVADA`; omitirlo
incluye todos. El listado publico filtra PUBLICADA en la consulta a la base,
independientemente de cualquier parametro `estado` enviado por el cliente.

El orden publico es `publicadaAt DESC, id ASC`; el administrativo es
`createdAt DESC, id ASC`. No se admite ordenamiento arbitrario. Las paginas
contienen `content`, `page`, `size`, `totalElements` y `totalPages`.
El resumen publico omite `contenido`, `estado` y los timestamps administrativos;
el detalle publico agrega `contenido`. Los endpoints administrativos incluyen
todos los campos del modelo. Nunca se exponen entidades JPA.

Errores con el formato comun de `ApiError`:

- 400: UUID, paginacion, estado o contenido invalidos; campos JSON desconocidos.
- 401: JWT ausente/invalido, usuario inactivo o rol que ya no coincide con el token.
- 403: falta de permisos, incluyendo intentos de escritura por las rutas publicas.
- 404: noticia inexistente; tambien borrador o archivada en la API publica,
  incluso si la consulta lleva un JWT administrativo.

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
   No enviar estado, fechas ni id en ese body: se rechazan con 400.
6. Retirar con el endpoint de estado y `{ "estado": "BORRADOR" }`, o archivar
   con `{ "estado": "ARCHIVADA" }`. Ambos la ocultan del listado y detalle publicos.

## Pruebas

```powershell
.\mvnw.cmd -B -ntp -Dtest=NoticiaIntegrationTest test
.\mvnw.cmd -B -ntp clean verify
```

La suite usa PostgreSQL temporal, migraciones reales y JWT firmados para usuarios
ADMIN/EDITOR. Verifica visibilidad, transiciones, permisos, validaciones,
paginacion, persistencia, concurrencia y el contrato generado de OpenAPI.
No utiliza Supabase real ni credenciales de produccion.
