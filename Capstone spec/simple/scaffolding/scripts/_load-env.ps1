<#
.SYNOPSIS
  Internal helper: parses the scaffold's .env file and sets values as
  process environment variables.

.DESCRIPTION
  PowerShell has no built-in dotenv loader. This handles:
    - blank lines and `# comment` lines (skipped)
    - KEY=VALUE
    - KEY="quoted value"
    - whitespace around `=` (trimmed)

  Existing process env vars take precedence over .env values (so a shell
  override beats the file). Dot-source this from the start-*.ps1 scripts.
#>

param(
    [string]$EnvFile = $(Join-Path $PSScriptRoot '..\.env')
)

if (-not (Test-Path $EnvFile)) {
    Write-Host "No .env at $EnvFile — using shell environment only." -ForegroundColor Yellow
    Write-Host "Copy .env.example to .env to customize (optional)." -ForegroundColor Yellow
    return
}

Write-Host "Loading env vars from $EnvFile" -ForegroundColor DarkCyan
Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line) { return }
    if ($line.StartsWith('#')) { return }

    $eq = $line.IndexOf('=')
    if ($eq -lt 1) { return }

    $key = $line.Substring(0, $eq).Trim()
    $val = $line.Substring($eq + 1).Trim()

    # Strip surrounding single or double quotes
    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or
        ($val.StartsWith("'") -and $val.EndsWith("'"))) {
        $val = $val.Substring(1, $val.Length - 2)
    }

    # Don't clobber an existing env var (lets shell-level overrides win)
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
        [Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}
