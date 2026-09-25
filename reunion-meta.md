# Puntos a definir para la integración con Meta (Instagram/Facebook)

Esta es la agenda de temas que necesitamos acordar entre Backend y Frontend antes de poder implementar la integración con Meta, tal como lo exige el hito actual de la maqueta.

## 1. El formato del JSON (El Contrato)
Necesito saber exactamente qué campos vas a requerir que la API te devuelva para dibujar cada publicación de Meta en el frontend.
- **Acción:** Pasame un JSON de ejemplo de cómo esperás recibir los datos.
- **Ejemplo a definir:** ¿Necesitás `id_post`, `texto`, `imagen_url`, `fecha_publicacion`, `cantidad_likes`, `link_original`?

## 2. Manejo de las Imágenes (Medios)
Tenemos que decidir qué hacemos con las fotos de los posts de Meta:
- **Opción A (Solo URL):** Guardamos solo la URL original que nos da Meta y el frontend carga la imagen directo desde los servidores de ellos. *(Ojo: hay que tener cuidado porque a veces las URLs temporales de Meta caducan a los pocos días).*
- **Opción B (Descarga local):** El backend descarga la imagen original desde Meta y la sube a nuestro propio Supabase Storage. Esto nos asegura que las imágenes nunca se van a romper ni expirar.

## 3. Mezcla en el Carrusel de Destacados (Ubicación)
La documentación del proyecto exige que los posts de Meta se puedan agregar al mismo Carrusel Web que ya usamos para Noticias y Eventos, manteniendo siempre el **límite máximo de 6 elementos** globales.
- Actualmente el endpoint de `GET /api/destacados` devuelve objetos con `noticiaId` o `eventoId`. Para incluir redes, voy a tener que agregar un `metaPostId`.
- **Acción:** Definir si vas a necesitar algún campo extra en el JSON del carrusel para saber cómo renderizar la tarjeta dinámicamente (por ejemplo, un campo `tipo: "META"` o similar).

## 4. Sincronización y Decisiones Editoriales
El backend va a tener que refrescar los posts de Meta conectándose a su API automáticamente. Pero la regla de negocio del instituto dice claramente que *"no debemos perder las decisiones editoriales al refrescar"*.
- **Lo que esto significa:** Si desde el panel de Admin de nuestro CMS decidimos ocultar un post específico de Meta, o lo marcamos como "Destacado", cuando mi backend vuelva a sincronizar contra la API de Meta para buscar posts nuevos, no debe pisar, sobreescribir ni borrar ese estado manual que ya configuramos.