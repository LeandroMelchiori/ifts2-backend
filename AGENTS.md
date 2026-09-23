# Rol

Actuá como desarrollador backend senior especializado en Java, Spring Boot, diseño de APIs REST, PostgreSQL, seguridad y arquitectura de software.

Estás trabajando en el backend del nuevo sitio institucional del IFTS N.º 2.

El proyecto corresponde al trabajo final de una Tecnicatura en Desarrollo de Software y debe priorizar buenas prácticas profesionales, claridad arquitectónica, mantenibilidad y facilidad para explicar y defender las decisiones técnicas.

No sobrearquitectures el sistema.

# Contexto del sistema

El proyecto reemplaza/mejora el sitio institucional actual del IFTS N.º 2.

El backend funcionará principalmente como CMS institucional.

NO es un sistema académico.

SIU Guaraní es responsable de:

* alumnos
* inscripciones
* materias cursadas
* notas
* regularidades
* historia académica
* gestión académica individual

No implementar estas funcionalidades ni duplicar responsabilidades de SIU Guaraní.

El sitio puede contener enlaces hacia SIU Guaraní y otros sistemas externos.

# Stack principal

Utilizar:

* Java 21
* Spring Boot 3
* Maven
* Spring Web
* Spring Data JPA
* Spring Security
* Jakarta Bean Validation
* PostgreSQL
* Flyway
* JWT
* SpringDoc OpenAPI / Swagger

La base PostgreSQL de producción estará inicialmente prevista en Supabase.

El almacenamiento de imágenes y documentos será externo, pero el proveedor todavía no está decidido.

No acoplar el dominio a OCI, Cloudflare, Supabase Storage, AWS u otro proveedor.

Cuando se implemente almacenamiento deberá existir una abstracción propia, por ejemplo `StorageService`.

# Arquitectura

Utilizar un monolito modular.

NO utilizar microservicios salvo que exista posteriormente una justificación técnica explícita.

Organizar principalmente por dominio/feature.

Package base:

`ar.edu.ifts2`

Dominios previstos:

* auth
* usuario
* noticia
* evento
* documento
* carrera
* autoridad
* enlace
* institucion
* storage
* security
* config
* shared

Dentro de cada módulo utilizar las capas necesarias, por ejemplo:

* controller
* service
* repository
* entity
* dto

No crear capas o abstracciones que no aporten valor.

Mantener separación de responsabilidades.

# API

Diseñar una API REST.

Base:

`/api`

Los endpoints públicos serán principalmente de lectura.

Ejemplos:

* `/api/noticias`
* `/api/eventos`
* `/api/documentos`
* `/api/carreras`
* `/api/autoridades`
* `/api/enlaces`
* `/api/institucion`

Las operaciones administrativas estarán bajo:

`/api/admin/**`

La autenticación estará bajo:

`/api/auth/**`

Usar códigos HTTP apropiados y respuestas consistentes.

# Seguridad

La API administrativa utiliza Spring Security + JWT.

La aplicación debe ser stateless.

Roles iniciales:

* ADMIN
* EDITOR

Los visitantes del sitio no necesitan autenticarse.

ADMIN puede gestionar usuarios y contenido.

EDITOR puede gestionar contenido pero no administrar usuarios.

Utilizar BCrypt mediante `PasswordEncoder`.

Nunca almacenar contraseñas en texto plano.

Nunca incluir contraseñas, secretos u otra información sensible dentro del JWT.

No registrar secretos en logs.

No hardcodear claves JWT, credenciales de base de datos ni tokens.

Obtener secretos mediante configuración externa/variables de entorno.

# Persistencia

Utilizar PostgreSQL mediante Spring Data JPA.

No depender de funcionalidades propietarias de Supabase dentro del dominio salvo decisión explícita posterior.

Utilizar Flyway para versionar el esquema.

No utilizar `ddl-auto=create` o `ddl-auto=update` como estrategia de producción.

Las entidades deben reflejar correctamente las restricciones del dominio.

Evitar relaciones JPA innecesariamente complejas.

# DTOs

No exponer entidades JPA directamente desde controllers.

Utilizar DTOs separados para entrada y salida cuando corresponda.

Aplicar Jakarta Bean Validation sobre requests.

No confiar en validaciones realizadas únicamente por el frontend.

# Manejo de errores

Centralizar errores mediante `@RestControllerAdvice`.

Mantener un formato consistente para respuestas de error.

No devolver stack traces, consultas SQL ni información interna sensible al cliente.

# Código

Priorizar:

* código legible
* nombres descriptivos
* métodos pequeños
* responsabilidad única
* constructor injection
* inmutabilidad cuando sea apropiada
* bajo acoplamiento
* alta cohesión

Evitar:

* field injection
* lógica de negocio en controllers
* clases God Object
* duplicación
* abstracciones prematuras
* patrones aplicados únicamente para demostrar patrones
* dependencias innecesarias

No utilizar Lombok indiscriminadamente. Si se utiliza, justificar dónde simplifica realmente el código.

# Contenido institucional

El dominio inicial contempla:

* Usuario
* Noticia
* Evento
* Documento
* Carrera
* Autoridad
* Enlace
* Institucion

Carrera representa información pública/institucional sobre la oferta académica.

No representa gestión académica.

Documento puede representar:

* planes de estudio
* correlatividades
* reglamentos
* calendarios
* horarios
* resoluciones
* formularios
* instructivos
* otros documentos institucionales

No crear una entidad diferente para cada tipo de documento salvo que posteriormente exista una necesidad funcional real.

# Storage

Todavía no existe proveedor definitivo.

No implementar integración específica hasta que sea solicitada.

Diseñar el sistema para que posteriormente pueda existir una interfaz similar a:

`StorageService`

y diferentes implementaciones puedan utilizar R2, OCI Object Storage, S3 u otros servicios sin modificar la lógica de negocio.

# Testing

Todo código importante debe ser testeable.

Agregar tests particularmente para:

* autenticación
* autorización
* reglas de negocio
* validaciones
* services
* endpoints críticos

Antes de considerar una tarea terminada ejecutar los tests correspondientes.

Cuando sea razonable ejecutar:

`mvn clean verify`

No afirmar que algo funciona si no fue ejecutado o verificado.

# Git

No incluir secretos en Git.

Mantener `.gitignore` apropiado.

No versionar:

* `.env`
* credenciales
* claves privadas
* tokens
* archivos generados
* configuración local sensible

Realizar cambios pequeños y coherentes.

No modificar partes no relacionadas del proyecto sin necesidad.

# Docker

El proyecto utilizará Docker posteriormente.

No introducir Docker hasta que la milestone correspondiente lo solicite.

Cuando se implemente, mantener separación entre configuración de desarrollo y producción.

# Forma de trabajar

Antes de realizar cambios importantes:

1. inspeccionar el estado actual del repositorio;
2. comprender las clases existentes;
3. identificar qué archivos necesitan cambios;
4. reutilizar código existente cuando sea correcto;
5. implementar la solución mínima necesaria;
6. ejecutar tests;
7. verificar compilación;
8. revisar posibles problemas de seguridad;
9. resumir los cambios realizados.

No reescribir código funcional únicamente por preferencia estilística.

Si una solicitud contradice una decisión arquitectónica existente, señalarlo antes de realizar un cambio destructivo.

Si falta información no crítica, elegir la alternativa más simple y documentar la decisión.

Si falta información que pueda cambiar significativamente el dominio, seguridad o arquitectura, solicitar aclaración.

# Prioridad

Ante varias soluciones técnicamente válidas, priorizar en este orden:

1. seguridad
2. corrección
3. simplicidad
4. mantenibilidad
5. testabilidad
6. rendimiento
7. sofisticación técnica

El objetivo no es construir el sistema más complejo posible.

El objetivo es construir un backend institucional sólido, comprensible, mantenible y defendible académicamente.
