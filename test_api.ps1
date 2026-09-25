$baseUrl = "https://ifts2-backend.onrender.com/api"
$email = "admin@ifts2.edu.ar"
$password = "Admin1234567!"

Write-Host "1. Haciendo Login..."
$loginBody = @{
    email = $email
    password = $password
} | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResponse.accessToken
Write-Host "Token obtenido correctamente."

Write-Host "2. Creando noticia..."
$noticiaBody = @{
    titulo = "¡Bienvenidos al nuevo sitio del IFTS 2!"
    resumen = "Estamos estrenando el nuevo CMS institucional."
    contenido = "Esta es una publicacion generada automaticamente para inicializar la base de datos."
    area = "GENERAL"
    mostrarEnNovedades = $true
} | ConvertTo-Json
$headers = @{ "Authorization" = "Bearer $token" }
$noticiaResponse = Invoke-RestMethod -Uri "$baseUrl/admin/noticias" -Method Post -Body $noticiaBody -Headers $headers -ContentType "application/json"
$noticiaId = $noticiaResponse.id
Write-Host "Noticia creada con ID: $noticiaId"

Write-Host "3. Publicando noticia..."
$estadoBody = '"PUBLICADA"'
Invoke-RestMethod -Uri "$baseUrl/admin/noticias/$noticiaId/estado" -Method Put -Body $estadoBody -Headers $headers -ContentType "application/json"
Write-Host "Noticia publicada."

Write-Host "4. Agregando al carrusel de destacados..."
$destacadosBody = @{
    items = @(
        @{ noticiaId = $noticiaId }
    )
} | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/admin/destacados" -Method Put -Body $destacadosBody -Headers $headers -ContentType "application/json"
Write-Host "Destacado guardado."

Write-Host "5. Cargando datos institucionales..."
$institucionBody = @{
    direccion = "Av. San Juan 2021, CABA"
    email = "contacto@ifts2.edu.ar"
    telefono = "+54 11 1234-5678"
    busquedaMapa = "Av. San Juan 2021, Buenos Aires, Argentina"
    sitioOficialUrl = "https://ifts2.edu.ar"
    instagramUrl = "https://www.instagram.com/ifts_2/"
    instagramVisible = $true
    facebookVisible = $false
} | ConvertTo-Json

# The endpoint expects the institucion to exist. V5 creates it?
# The markdown says: "Si no existe Institucion devuelve 404. Si ya esta publicada, los cambios son inmediatos. Crear primero la institucion con PUT /api/admin/institucion."
# So I must create it first.
$institucionBase = @{
    nombre = "IFTS N. 2"
    descripcion = "Instituto de Formación Técnica Superior N. 2"
    horarios = "Lunes a Viernes de 18:30 a 22:30"
} | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/admin/institucion" -Method Put -Body $institucionBase -Headers $headers -ContentType "application/json" -ErrorAction SilentlyContinue

Invoke-RestMethod -Uri "$baseUrl/admin/institucion/datos-sitio" -Method Put -Body $institucionBody -Headers $headers -ContentType "application/json"
Write-Host "Datos institucionales guardados."

Write-Host "¡Todo listo!"
