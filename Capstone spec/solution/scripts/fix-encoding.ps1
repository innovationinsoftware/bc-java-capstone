$docsDir = "c:\Users\ishea\OneDrive\Documents\Business\Innovation in Software\bc-java-capstone\Capstone spec\solution\docs"
$utf8 = [System.Text.Encoding]::UTF8
$cp1252 = [System.Text.Encoding]::GetEncoding(1252)
foreach ($file in (Get-ChildItem $docsDir -Filter "*.md")) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    # Detect double-encoding by looking for byte seq C3 83 C2 (? followed by Latin char)
    $doubled = $false
    for ($i = 0; $i -lt $bytes.Length - 2; $i++) {
        if ($bytes[$i] -eq 0xC3 -and $bytes[$i+1] -eq 0x83 -and $bytes[$i+2] -eq 0xC2) { $doubled = $true; break }
    }
    if ($doubled) {
        $s1 = $utf8.GetString($bytes)
        $s2 = $utf8.GetString($cp1252.GetBytes($s1))
        $s3 = $utf8.GetString($cp1252.GetBytes($s2))
        [System.IO.File]::WriteAllBytes($file.FullName, $utf8.GetBytes($s3))
        Write-Output "Fixed (2 passes): $($file.Name)"
    } else {
        Write-Output "OK: $($file.Name)"
    }
}
Write-Output ""
Write-Output "--- First line of setup.md after fix ---"
$check = $utf8.GetString([System.IO.File]::ReadAllBytes("$docsDir\setup.md"))
$check.Split("`n")[0]
