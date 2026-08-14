[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'config\local.json'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$skillRoot = Split-Path $PSScriptRoot -Parent

if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
    throw "Runqian local configuration already exists: $OutputPath. Validate and reuse it; pass -Force only when the recorded installation paths are confirmed stale."
}

function Resolve-CommandPath {
    param([Parameter(Mandatory)][string]$Name)

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }
    return $command.Source
}

function Find-RunqianHomes {
    $candidates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $roots = @(
        $env:RAQSOFT_HOME,
        $env:RUNQIAN_HOME,
        'C:\raqsoft\report',
        'D:\raqsoft\report',
        'C:\soft\raqsoft\report',
        'D:\soft\raqsoft\report',
        (Join-Path $env:ProgramFiles 'raqsoft\report'),
        (Join-Path ${env:ProgramFiles(x86)} 'raqsoft\report')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($root in $roots) {
        $resolved = [Environment]::ExpandEnvironmentVariables($root)
        if (Test-Path -LiteralPath (Join-Path $resolved 'bin\report.exe')) {
            [void]$candidates.Add((Resolve-Path -LiteralPath $resolved).Path)
        }
    }

    # Search only fixed local drives and bounded directory depth to avoid scanning projects or network disks.
    foreach ($drive in Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3') {
        foreach ($pattern in @('raqsoft\report', 'soft\raqsoft\report', 'Program Files\raqsoft\report', 'Program Files (x86)\raqsoft\report')) {
            $candidate = Join-Path $drive.DeviceID $pattern
            if (Test-Path -LiteralPath (Join-Path $candidate 'bin\report.exe')) {
                [void]$candidates.Add((Resolve-Path -LiteralPath $candidate).Path)
            }
        }
    }

    return @($candidates)
}

$java = Resolve-CommandPath -Name 'java'
$javac = Resolve-CommandPath -Name 'javac'
$homes = @(Find-RunqianHomes)

if ([string]::IsNullOrWhiteSpace($java) -or [string]::IsNullOrWhiteSpace($javac)) {
    throw 'Java JDK is required: both java and javac must be available on PATH.'
}
if ($homes.Count -eq 0) {
    throw 'Runqian installation was not found. Install Runqian or set RAQSOFT_HOME/RUNQIAN_HOME, then rerun.'
}
if ($homes.Count -gt 1) {
    throw "Multiple Runqian installations were found. Set RAQSOFT_HOME to the intended report directory: $($homes -join ', ')"
}

$runqianHome = $homes[0]
$designer = Join-Path $runqianHome 'bin\report.exe'
$licenseCandidates = @(
    Get-ChildItem -LiteralPath $runqianHome -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)license.*\.xml$|defaultlicense.*\.xml$' } |
        Select-Object -ExpandProperty FullName -Unique
)

if ($licenseCandidates.Count -eq 0) {
    throw 'No Runqian license XML was found. Configure a valid local license before using ReportUtils.read().'
}
if ($licenseCandidates.Count -gt 1) {
    throw "Multiple Runqian license files were found. Review them and write the intended path to config\local.json: $($licenseCandidates -join ', ')"
}

$runqianParent = Split-Path $runqianHome -Parent
$classpathEntries = @(
    (Join-Path $runqianHome 'lib\*'),
    (Join-Path $runqianHome 'web\webapps\demo\WEB-INF\lib\*'),
    (Join-Path $runqianHome 'web\webapps\demo\WEB-INF\classes'),
    (Join-Path $runqianHome 'web\tomcat\lib\*'),
    (Join-Path $runqianParent 'common\jdbc\*')
)

$missingClasspathRoots = @($classpathEntries | Where-Object {
    $probe = $_ -replace '\\\*$', ''
    -not (Test-Path -LiteralPath $probe)
})
if ($missingClasspathRoots.Count -gt 0) {
    throw "Required Runqian classpath directories are missing: $($missingClasspathRoots -join ', ')"
}

$configuration = [ordered]@{
    javaExecutable     = $java
    javacExecutable    = $javac
    runqianHome        = $runqianHome
    designerExecutable = $designer
    licenseFile        = $licenseCandidates[0]
    classpathEntries   = $classpathEntries
}

$outputDirectory = Split-Path $OutputPath -Parent
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$configuration | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Output "Created local configuration: $OutputPath"
Write-Output 'Review every path before modifying an RPX.'
