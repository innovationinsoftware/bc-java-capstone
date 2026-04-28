<#
.SYNOPSIS
  Install (if needed) and run the Vite dev server on port 5173.

.DESCRIPTION
  Requires Node 20+ and npm. The first run downloads dependencies — that
  takes a few minutes. The dev server proxies /api,/login,/logout,/oauth2
  to the BFF on 8080 (see vite.config.js).

  Stop with Ctrl-C.
#>

$ErrorActionPreference = 'Stop'
$ScriptDir    = $PSScriptRoot
$ScaffoldDir  = Resolve-Path (Join-Path $ScriptDir '..')
$FrontendDir  = Join-Path $ScaffoldDir 'frontend'

Set-Location $FrontendDir

if (-not (Test-Path 'node_modules')) {
    Write-Host "node_modules not found — running npm install (one-time)..." -ForegroundColor DarkCyan
    & npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& npm run dev
