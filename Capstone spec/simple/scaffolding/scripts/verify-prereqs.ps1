<#
.SYNOPSIS
  Verifies the dev VM has everything the capstone scaffold needs.

.DESCRIPTION
  Checks tool versions (Java 17+, Maven 3.9+, Node 20+, npm, sqlplus,
  curl), confirms the expected ports are free, and probes Oracle on
  localhost:1521. Prints a green/red summary at the end.

  Run BEFORE doing anything else. Paste the summary into a chat so the
  instructor can see your environment.

.EXAMPLE
  PS> .\scripts\verify-prereqs.ps1
#>

$ErrorActionPreference = 'Continue'
$results = @()

function Add-Result {
    param($Name, [bool]$Ok, $Detail)
    $script:results += [pscustomobject]@{
        Name   = $Name
        Ok     = $Ok
        Detail = $Detail
    }
}

function Test-Command {
    param([string]$Name, [string]$Cmd, [string]$VersionArg, [scriptblock]$Parse, [string]$MinVersion)
    $exe = Get-Command $Cmd -ErrorAction SilentlyContinue
    if (-not $exe) {
        Add-Result $Name $false "$Cmd not found in PATH"
        return
    }
    try {
        $output = & $Cmd $VersionArg 2>&1 | Out-String
        $version = & $Parse $output
        if ([string]::IsNullOrWhiteSpace($version)) {
            Add-Result $Name $false "could not parse version from output"
            return
        }
        if ($MinVersion) {
            $v = [version]($version -split '[^0-9.]' | Where-Object { $_ } | Select-Object -First 1)
            $min = [version]$MinVersion
            if ($v -ge $min) {
                Add-Result $Name $true "$version (min $MinVersion)"
            } else {
                Add-Result $Name $false "$version is below minimum $MinVersion"
            }
        } else {
            Add-Result $Name $true $version
        }
    } catch {
        Add-Result $Name $false "error invoking ${Cmd}: $_"
    }
}

function Test-PortFree {
    param([int]$Port, [string]$ExpectedOwner)
    # Try the modern cmdlet first; if not available, fall back to opening a TCP listener.
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($busy) {
            $pidlist = ($busy.OwningProcess | Sort-Object -Unique) -join ','
            Add-Result "Port $Port ($ExpectedOwner) free" $false "in use by PID $pidlist"
            return
        }
        Add-Result "Port $Port ($ExpectedOwner) free" $true "ok"
        return
    }
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        Add-Result "Port $Port ($ExpectedOwner) free" $true "ok (fallback probe)"
    } catch {
        Add-Result "Port $Port ($ExpectedOwner) free" $false "in use (fallback probe)"
    }
}

Write-Host "Capstone — environment prerequisites" -ForegroundColor Cyan
Write-Host "===================================="

# --- Tool versions ---
Test-Command "Java 17+" "java" "-version" {
    param($s); ($s -split "`n")[0] -replace '.*"([0-9.]+)".*', '$1'
} "17"

Test-Command "Maven 3.9+" "mvn" "-version" {
    param($s); ($s -split "`n")[0] -replace '.*Apache Maven ([0-9.]+).*', '$1'
} "3.9"

Test-Command "Node.js 20+" "node" "--version" {
    param($s); ($s -split "`n")[0] -replace 'v',''
} "20"

Test-Command "npm 10+" "npm" "--version" {
    param($s); ($s -split "`n")[0]
} "10"

Test-Command "sqlplus (Oracle client)" "sqlplus" "-V" {
    param($s); if ($s -match 'Release ([0-9.]+)') { $matches[1] } else { '' }
}

Test-Command "curl" "curl" "--version" {
    param($s); ($s -split "`n")[0] -replace 'curl ([0-9.]+).*', '$1'
}

# --- Ports the four services will occupy ---
Test-PortFree 9000 "mock-auth"
Test-PortFree 8081 "resource-server"
Test-PortFree 8080 "bff"
Test-PortFree 5173 "frontend (vite)"
Test-PortFree 8089 "wiremock"

# --- Oracle reachability on localhost:1521 ---
function Test-Tcp {
    param($Host_, $Port)
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $task = $client.ConnectAsync($Host_, $Port)
        if ($task.Wait(2000)) {
            $client.Close(); return $true
        }
        $client.Close(); return $false
    } catch { return $false }
}
if (Test-Tcp 'localhost' 1521) {
    Add-Result "Oracle TCP 1521" $true "listening on localhost:1521"
} else {
    Add-Result "Oracle TCP 1521" $false "no listener (Oracle XE not running?)"
}

# --- Internet reachability for Maven Central + npm registry (one ping each) ---
function Test-Web {
    param($Name, $Url)
    try {
        $r = Invoke-WebRequest -Uri $Url -Method Head -TimeoutSec 5 -UseBasicParsing
        Add-Result $Name $true "HTTP $($r.StatusCode)"
    } catch {
        Add-Result $Name $false "unreachable"
    }
}
Test-Web "Maven Central reachable" "https://repo1.maven.org/"
Test-Web "npm registry reachable" "https://registry.npmjs.org/"

# --- Summary ---
Write-Host ""
Write-Host "Summary"  -ForegroundColor Cyan
Write-Host "-------"
foreach ($r in $results) {
    $marker = if ($r.Ok) { '[OK]  ' } else { '[FAIL]' }
    $color  = if ($r.Ok) { 'Green' } else { 'Red' }
    Write-Host ("{0} {1,-32} {2}" -f $marker, $r.Name, $r.Detail) -ForegroundColor $color
}
$failed = ($results | Where-Object { -not $_.Ok }).Count
Write-Host ""
if ($failed -eq 0) {
    Write-Host "All prerequisites OK — you can run the start scripts." -ForegroundColor Green
    exit 0
} else {
    Write-Host ("$failed check(s) failed. Address the [FAIL] rows above before continuing.") -ForegroundColor Red
    exit 1
}
