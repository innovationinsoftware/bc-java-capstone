<#
.SYNOPSIS
  Start the mock Authorization Server on port 9000.

.DESCRIPTION
  This must be the FIRST service started. Both the BFF and the Resource
  Server fetch /.well-known/openid-configuration from it on boot.

  Pre-registered users (in-memory):
    alice / alice  (CUSTOMER)
    admin / admin  (ADMIN — set via UPDATE BANK_USERS in Oracle)

  Stop with Ctrl-C.
#>

$ErrorActionPreference = 'Stop'
$ScriptDir   = $PSScriptRoot
$ScaffoldDir = Resolve-Path (Join-Path $ScriptDir '..')

# .env not strictly needed for mock-auth, but load it for consistency
. (Join-Path $ScriptDir '_load-env.ps1')

Set-Location (Join-Path $ScaffoldDir 'backend')
& mvn -pl mock-auth -am spring-boot:run
