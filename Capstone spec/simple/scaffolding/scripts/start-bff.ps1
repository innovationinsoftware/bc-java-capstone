<#
.SYNOPSIS
  Start the BFF on port 8080.

.DESCRIPTION
  Requires:
    - JDK 17, Maven 3.9+
    - mock-auth running on port 9000 (BFF fetches discovery at boot)
    - resource-server running on port 8081 (BFF proxies to it)
  Stop with Ctrl-C.
#>

$ErrorActionPreference = 'Stop'
$ScriptDir   = $PSScriptRoot
$ScaffoldDir = Resolve-Path (Join-Path $ScriptDir '..')

. (Join-Path $ScriptDir '_load-env.ps1')

Set-Location (Join-Path $ScaffoldDir 'backend')
& mvn -pl bff -am spring-boot:run
