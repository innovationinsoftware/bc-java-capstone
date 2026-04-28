<#
.SYNOPSIS
  Start WireMock standalone on port 8089 with the stubs in wiremock-stubs/.

.DESCRIPTION
  WireMock impersonates the external Payment Processor for local
  development. The first run downloads the JAR (~25 MB) into
  scripts/.cache/. Subsequent runs reuse it.

  Stop with Ctrl-C.
#>

$ErrorActionPreference = 'Stop'

$WiremockVersion = '3.6.0'
$ScriptDir       = $PSScriptRoot
$ScaffoldDir     = Resolve-Path (Join-Path $ScriptDir '..')
$CacheDir        = Join-Path $ScriptDir '.cache'
$Jar             = Join-Path $CacheDir "wiremock-standalone-$WiremockVersion.jar"
$Url             = "https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/$WiremockVersion/wiremock-standalone-$WiremockVersion.jar"

if (-not (Test-Path $CacheDir)) {
    New-Item -ItemType Directory -Path $CacheDir | Out-Null
}

if (-not (Test-Path $Jar)) {
    Write-Host "Downloading WireMock $WiremockVersion..." -ForegroundColor DarkCyan
    Invoke-WebRequest -Uri $Url -OutFile $Jar -UseBasicParsing
}

Set-Location $ScaffoldDir
Write-Host "Starting WireMock on http://localhost:8089" -ForegroundColor Cyan
Write-Host "Stubs from: $ScaffoldDir\wiremock-stubs\mappings"
Write-Host ""

& java -jar $Jar `
    --port 8089 `
    --root-dir wiremock-stubs `
    --global-response-templating `
    --verbose
