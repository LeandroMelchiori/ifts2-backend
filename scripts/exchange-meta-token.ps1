# Intercambia un User token corto (Graph Explorer) por long-lived (~60 dias).
# Uso (NO subas secretos al repo):
#   $env:META_APP_ID="..."
#   $env:META_APP_SECRET="..."
#   $env:META_SHORT_TOKEN="..."
#   .\backend\scripts\exchange-meta-token.ps1
#
# Copiá el access_token resultante a Render como META_ACCESS_TOKEN.

$ErrorActionPreference = 'Stop'
$appId = $env:META_APP_ID
$secret = $env:META_APP_SECRET
$short = $env:META_SHORT_TOKEN
$version = if ($env:META_API_VERSION) { $env:META_API_VERSION } else { 'v21.0' }

if (-not $appId -or -not $secret -or -not $short) {
  Write-Error 'Definí META_APP_ID, META_APP_SECRET y META_SHORT_TOKEN en el entorno.'
}

$uri = "https://graph.facebook.com/$version/oauth/access_token" +
  "?grant_type=fb_exchange_token" +
  "&client_id=$([uri]::EscapeDataString($appId))" +
  "&client_secret=$([uri]::EscapeDataString($secret))" +
  "&fb_exchange_token=$([uri]::EscapeDataString($short))"

Write-Host "GET $version/oauth/access_token (secret omitido en log)..."
$res = Invoke-RestMethod -Method Get -Uri $uri
Write-Host "expires_in=$($res.expires_in)"
Write-Host "access_token=$($res.access_token)"
Write-Host ""
Write-Host "Pegá access_token en Render → META_ACCESS_TOKEN y activá META_ENABLED=true."
