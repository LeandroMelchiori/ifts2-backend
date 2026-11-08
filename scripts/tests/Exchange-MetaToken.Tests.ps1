# Tests locales sin llamadas a Meta ni acceso al portapapeles real; sin dependencias.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$scriptPath = Join-Path $PSScriptRoot '../exchange-meta-token.ps1'
$savedEnvironment = @{}
foreach ($name in @('META_APP_ID', 'META_API_VERSION', 'META_APP_SECRET', 'META_SHORT_TOKEN')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}
$global:metaTestsecretFixture = [guid]::NewGuid().ToString() + '&?='
$global:metaTestshortFixture = [guid]::NewGuid().ToString() + '&?='
$global:metaTestlongFixture = [guid]::NewGuid().ToString()

function Assert-True($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-RestMethod {
    [CmdletBinding()]
    param($Method, $Uri, $TimeoutSec, $MaximumRedirection)
    $global:metaTesthttpCalls++
    Assert-True ($Method -eq 'Get' -and $TimeoutSec -eq 30 -and $MaximumRedirection -eq 0) 'HTTP sin limites esperados'
    Assert-True ($Uri.StartsWith('https://graph.facebook.com/v99.0/oauth/access_token?')) 'Destino inesperado'
    Assert-True ($Uri.Contains([uri]::EscapeDataString($global:metaTestsecretFixture))) 'Secret sin codificar'
    Assert-True ($Uri.Contains([uri]::EscapeDataString($global:metaTestshortFixture))) 'Token sin codificar'
    Assert-True ($PSBoundParameters['Verbose'] -eq $false -and $PSBoundParameters['Debug'] -eq $false) 'Trazas no deshabilitadas'
    if ($global:metaTestmode -eq 'http-error') { throw "Provider failure $Uri" }
    if ($global:metaTestmode -eq 'invalid-response') { return [pscustomobject]@{error=$global:metaTestsecretFixture} }
    if ($global:metaTestmode -eq 'no-expiry') { return [pscustomobject]@{access_token=$global:metaTestlongFixture} }
    return [pscustomobject]@{access_token=$global:metaTestlongFixture; expires_in=5184000}
}

function Set-Clipboard {
    [CmdletBinding()]
    param($Value)
    if ($global:metaTestmode -eq 'clipboard-error') { throw "Clipboard failure $Value" }
    $global:metaTestclipboard = $Value
}

function Run-Scenario([string]$Mode, [bool]$ExpectedFailure, [int]$ExpectedCalls, [bool]$ExpectedCopy) {
    $global:metaTestmode = $Mode
    $global:metaTesthttpCalls = 0
    $global:metaTestclipboard = $null
    $env:META_APP_ID = '123456'
    $env:META_API_VERSION = 'v99.0' # Solo fixture; nunca se consulta Meta.
    $env:META_APP_SECRET = $global:metaTestsecretFixture
    $env:META_SHORT_TOKEN = $global:metaTestshortFixture
    if ($Mode -eq 'missing-version') { $env:META_API_VERSION = '' }
    if ($Mode -eq 'invalid-version') { $env:META_API_VERSION = 'v99.0/other' }
    if ($Mode -eq 'missing-secret') { $env:META_APP_SECRET = '' }
    if ($Mode -eq 'invalid-app') { $env:META_APP_ID = 'invalid' }
    $failed = $false
    $messages = @()
    try {
        if ($Mode -eq 'no-consent') { $messages = @(& $scriptPath *>&1) }
        else { $messages = @(& $scriptPath -CopyToClipboard *>&1) }
    } catch {
        $failed = $true
        $messages += ($_ | Out-String)
    }
    $text = $messages | Out-String
    foreach ($value in @($global:metaTestsecretFixture, $global:metaTestshortFixture, $global:metaTestlongFixture,
            [uri]::EscapeDataString($global:metaTestsecretFixture), [uri]::EscapeDataString($global:metaTestshortFixture))) {
        Assert-True (-not $text.Contains($value)) "Credencial expuesta en caso $Mode"
    }
    Assert-True ($failed -eq $ExpectedFailure) "Resultado incorrecto en $Mode"
    Assert-True ($global:metaTesthttpCalls -eq $ExpectedCalls) "Cantidad HTTP incorrecta en $Mode"
    if ($ExpectedCopy) {
        Assert-True ($global:metaTestclipboard -eq $global:metaTestlongFixture) "Copia incorrecta en $Mode"
    } else {
        Assert-True ($null -eq $global:metaTestclipboard) "Copia inesperada en $Mode"
    }
    Write-Output "PASS $Mode"
}

try {
    Run-Scenario 'success' $false 1 $true
    Run-Scenario 'no-expiry' $false 1 $true
    Run-Scenario 'http-error' $true 1 $false
    Run-Scenario 'invalid-response' $true 1 $false
    Run-Scenario 'clipboard-error' $true 1 $false
    Run-Scenario 'no-consent' $true 0 $false
    Run-Scenario 'missing-version' $true 0 $false
    Run-Scenario 'invalid-version' $true 0 $false
    Run-Scenario 'missing-secret' $true 0 $false
    Run-Scenario 'invalid-app' $true 0 $false
    Write-Output '10 tests aprobados; Meta y portapapeles simulados.'
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name])
    }
}
