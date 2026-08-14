[CmdletBinding(DefaultParameterSetName = 'Inspect')]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Inspect', 'Patch')]
    [string]$Action,

    [Parameter(Mandatory, ParameterSetName = 'Inspect')]
    [string[]]$Path,

    [Parameter(Mandatory, ParameterSetName = 'Patch')]
    [string]$TargetPath,

    [Parameter(Mandatory, ParameterSetName = 'Patch')]
    [string]$PatchJson,

    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$skillRoot = Split-Path $PSScriptRoot -Parent
$environment = & (Join-Path $PSScriptRoot 'validate-local-environment.ps1')
$source = Join-Path $PSScriptRoot 'java\RpxTool.java'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'Codex\runqian-report-making\rpx-tool'
$classes = Join-Path $cacheRoot 'classes'
$classFile = Join-Path $classes 'RpxTool.class'

# 编译缓存属于本机工具状态，不污染业务项目和技能源码目录。
$needsBuild = $Rebuild -or -not (Test-Path -LiteralPath $classFile) -or
    (Get-Item -LiteralPath $source).LastWriteTimeUtc -gt (Get-Item -LiteralPath $classFile).LastWriteTimeUtc
$classpath = @((Join-Path $environment.RunqianHome 'classes')) + @($environment.ClasspathEntries)
$classpathText = $classpath -join ';'

if ($needsBuild) {
    [void][IO.Directory]::CreateDirectory($classes)
    & $environment.JavacExecutable -encoding UTF-8 -cp $classpathText -d $classes $source
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to compile persistent Runqian object-model tool, exit code: $LASTEXITCODE"
    }
}

$runtimeClasspath = "$classes;$classpathText"
$javaArgs = @(
    "-Dstart.home=$($environment.RunqianHome)",
    '-cp',
    $runtimeClasspath,
    'RpxTool',
    $Action.ToLowerInvariant(),
    $environment.LicenseFile
)

if ($Action -eq 'Inspect') {
    $resolvedPaths = @($Path | ForEach-Object {
        if (-not (Test-Path -LiteralPath $_ -PathType Leaf)) {
            throw "RPX file does not exist: $_"
        }
        (Resolve-Path -LiteralPath $_).Path
    })
    & $environment.JavaExecutable @javaArgs @resolvedPaths
} else {
    if (-not (Test-Path -LiteralPath $TargetPath -PathType Leaf)) {
        throw "RPX file does not exist: $TargetPath"
    }
    $resolvedTarget = (Resolve-Path -LiteralPath $TargetPath).Path
    $PatchJson | & $environment.JavaExecutable @javaArgs $resolvedTarget
}

if ($LASTEXITCODE -ne 0) {
    throw "Persistent Runqian object-model tool failed, exit code: $LASTEXITCODE"
}
