# Requires PostgreSQL client tools and a private, encrypted destination outside Git.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PgBin,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

foreach ($name in @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing environment variable: $name"
    }
}
if ($env:PGDATABASE -match '[:=/\\\s]') { throw 'PGDATABASE must be a database name, not a connection string.' }
if ($env:PGHOST -notin @('localhost', '127.0.0.1', '::1') -and $env:PGSSLMODE -ne 'verify-full') {
    throw 'Remote backups require PGSSLMODE=verify-full and a trusted root certificate.'
}
$output = (Resolve-Path -LiteralPath $OutputDirectory).Path
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ($output.Equals($repo, [StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backups must be stored outside the repository.'
}
$dumpTool = Join-Path $PgBin 'pg_dump.exe'
$restoreTool = Join-Path $PgBin 'pg_restore.exe'
if (-not (Test-Path -LiteralPath $dumpTool)) { $dumpTool = Join-Path $PgBin 'pg_dump' }
if (-not (Test-Path -LiteralPath $restoreTool)) { $restoreTool = Join-Path $PgBin 'pg_restore' }
if (-not (Test-Path -LiteralPath $dumpTool) -or -not (Test-Path -LiteralPath $restoreTool)) {
    throw 'pg_dump and pg_restore are required.'
}
$tables = @('usuarios','noticias','eventos','carreras','autoridades','enlaces',
    'documentos','institucion','carrusel','destacados','evento_fotos','meta_posts',
    'panel_role','panel_role_permission','flyway_schema_history')
$name = 'ifts2-cms-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N')
$partial = Join-Path $output ($name + '.partial')
$archive = Join-Path $output ($name + '.dump')
$arguments = @('--format=custom', '--no-password', '--strict-names', '--lock-wait-timeout=15s', "--file=$partial")
foreach ($table in $tables) { $arguments += "--table=public.$table" }

try {
    # libpq reads PG* / PGPASSFILE; no passwords in arguments or output.
    & $dumpTool @arguments
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; no completed backup was produced.' }
    & $restoreTool --list $partial | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The archive cannot be read by pg_restore.' }
    $stream = [IO.File]::OpenRead($partial)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { $hash = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $algorithm.Dispose() }
    $manifest = [ordered]@{
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        archive = [IO.Path]::GetFileName($archive)
        sha256 = $hash
        tables = $tables
        scope = 'CMS tables including panel permissions; excludes Storage files, Supabase internals, PostgreSQL roles and environment secrets'
        restoreTested = $false
    }
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath ($partial + '.json') -Encoding UTF8
    Move-Item -LiteralPath $partial -Destination $archive
    Move-Item -LiteralPath ($partial + '.json') -Destination ($archive + '.json')
    Write-Output "Backup created: $archive"
    Write-Output 'Archive readable and SHA256 recorded. A restore rehearsal is still required.'
} finally {
    # Only this invocation's unique partial file; never prune older backups.
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial }
    if (Test-Path -LiteralPath ($partial + '.json')) { Remove-Item -LiteralPath ($partial + '.json') }
}
