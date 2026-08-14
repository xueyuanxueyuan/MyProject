[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'config\local.json')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Runqian local configuration does not exist: $ConfigPath. Run detect-local-environment.ps1 once."
}

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$requiredFiles = @(
    $config.javaExecutable,
    $config.javacExecutable,
    $config.designerExecutable,
    $config.licenseFile
)
$requiredDirectories = @($config.runqianHome)
$requiredDirectories += @($config.classpathEntries | ForEach-Object { $_ -replace '\\\*$', '' })

$missingFiles = @($requiredFiles | Where-Object {
    [string]::IsNullOrWhiteSpace($_) -or -not (Test-Path -LiteralPath $_ -PathType Leaf)
})
$missingDirectories = @($requiredDirectories | Where-Object {
    [string]::IsNullOrWhiteSpace($_) -or -not (Test-Path -LiteralPath $_ -PathType Container)
})

if ($missingFiles.Count -gt 0 -or $missingDirectories.Count -gt 0) {
    throw "Runqian local configuration is stale. Missing files: $($missingFiles -join ', '); missing directories: $($missingDirectories -join ', '). Rerun detect-local-environment.ps1 after reviewing the local installation."
}

[pscustomobject]@{
    Valid              = $true
    ConfigPath         = (Resolve-Path -LiteralPath $ConfigPath).Path
    JavaExecutable     = $config.javaExecutable
    JavacExecutable    = $config.javacExecutable
    RunqianHome        = $config.runqianHome
    DesignerExecutable = $config.designerExecutable
    LicenseFile        = $config.licenseFile
    ClasspathEntries   = @($config.classpathEntries)
}
