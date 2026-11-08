# Intercambia un User token corto (Graph Explorer) por long-lived (~60 dias).
# Uso (NO subas secretos al repo):
#   $env:META_APP_ID="..."
#   $env:META_APP_SECRET="..."
#   $env:META_SHORT_TOKEN="..."
#   .\scripts\exchange-meta-token.ps1 -CopyToClipboard
#
# Basado en el aporte de Carlos Zarate (PR #1). No ejecutar en CI ni al arrancar el backend.
[CmdletBinding()]
param([switch]$CopyToClipboard)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $CopyToClipboard) {
    throw 'Usar -CopyToClipboard para autorizar la copia local del token; no se imprime ni se guarda en archivos.'
}
if (-not (Get-Command Set-Clipboard -ErrorAction SilentlyContinue)) {
    throw 'Esta operacion requiere un portapapeles local disponible.'
}
$appId = $env:META_APP_ID
$secret = $env:META_APP_SECRET
$short = $env:META_SHORT_TOKEN
$version = $env:META_API_VERSION

if ([string]::IsNullOrWhiteSpace($appId) -or [string]::IsNullOrWhiteSpace($secret) -or
    [string]::IsNullOrWhiteSpace($short)) {
    throw 'Definir META_APP_ID, META_APP_SECRET y META_SHORT_TOKEN en el entorno local.'
}
if ($appId -notmatch '^[0-9]+$' -or $version -notmatch '^v[0-9]+\.[0-9]+$') {
    throw 'META_APP_ID debe ser numerico y META_API_VERSION una version explicita vN.N habilitada para la app.'
}

$uri = "https://graph.facebook.com/$version/oauth/access_token" +
  "?grant_type=fb_exchange_token" +
  "&client_id=$([uri]::EscapeDataString($appId))" +
  "&client_secret=$([uri]::EscapeDataString($secret))" +
  "&fb_exchange_token=$([uri]::EscapeDataString($short))"

try {
    try {
        # Evitar trazas de URI/cuerpo y redirecciones que propaguen credenciales.
        $result = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 30 -MaximumRedirection 0 `
            -Verbose:$false -Debug:$false -ErrorAction Stop
    } catch {
        throw 'No se pudo intercambiar el token. Revisar conectividad, version, app y credenciales en Meta. Detalles sensibles omitidos.'
    }
    $tokenProperty = $result.PSObject.Properties['access_token']
    if ($null -eq $tokenProperty -or $tokenProperty.Value -isnot [string] -or
        [string]::IsNullOrWhiteSpace($tokenProperty.Value)) {
        throw 'Meta no devolvio un access_token valido. Respuesta omitida por seguridad.'
    }
    try {
        Set-Clipboard -Value $tokenProperty.Value -ErrorAction Stop
    } catch {
        throw 'No se pudo copiar el token al portapapeles. No se imprimio ni se guardo en un archivo.'
    }
    Write-Host 'Token copiado al portapapeles. Pegar solamente en META_ACCESS_TOKEN de Render y luego limpiar el portapapeles.'
    $expiry = $result.PSObject.Properties['expires_in']
    [long]$seconds = 0
    if ($null -ne $expiry -and [long]::TryParse([string]$expiry.Value, [ref]$seconds) -and $seconds -gt 0) {
        Write-Host "expires_in=$seconds segundos. Verificar tambien permisos y vigencia en Meta."
    } else {
        Write-Host 'Sin expires_in valido: no asumir una duracion; verificar la vigencia en Meta.'
    }
} finally {
    $uri = $null
    $secret = $null
    $short = $null
    $result = $null
    $tokenProperty = $null
}
