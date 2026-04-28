<#
.SYNOPSIS
  Start the Resource Server (banking API) on port 8081.

.DESCRIPTION
  Requires:
    - JDK 17, Maven 3.9+
    - Oracle XE reachable at $env:ORACLE_URL (default jdbc:...XEPDB1)
    - mock-auth running on port 9000 (RS fetches its JWKS at boot)
  Stop with Ctrl-C.
#>

$ErrorActionPreference = 'Stop'
$ScriptDir   = $PSScriptRoot
$ScaffoldDir = Resolve-Path (Join-Path $ScriptDir '..')

. (Join-Path $ScriptDir '_load-env.ps1')

Set-Location (Join-Path $ScaffoldDir 'backend')
& mvn -pl resource-server -am spring-boot:run
