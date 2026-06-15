$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$output = Join-Path $root "jars"
$gradle = Join-Path $root "gradlew.bat"
$java21Home = $env:JAVA_HOME
$bundledJava25 = Join-Path $root "..\..\jbr"

$targets21 = @(
    @{ Version = "1.21.1"; Yarn = "1.21.1+build.3"; Api = "0.116.12+1.21.1"; Variant = "legacy" },
    @{ Version = "1.21.2"; Yarn = "1.21.2+build.1"; Api = "0.106.1+1.21.2"; Variant = "legacy" },
    @{ Version = "1.21.3"; Yarn = "1.21.3+build.2"; Api = "0.114.1+1.21.3"; Variant = "legacy" },
    @{ Version = "1.21.4"; Yarn = "1.21.4+build.8"; Api = "0.119.4+1.21.4"; Variant = "legacy" },
    @{ Version = "1.21.5"; Yarn = "1.21.5+build.1"; Api = "0.128.2+1.21.5"; Variant = "modern" },
    @{ Version = "1.21.6"; Yarn = "1.21.6+build.1"; Api = "0.128.2+1.21.6"; Variant = "modern" },
    @{ Version = "1.21.7"; Yarn = "1.21.7+build.8"; Api = "0.129.0+1.21.7"; Variant = "modern" },
    @{ Version = "1.21.8"; Yarn = "1.21.8+build.1"; Api = "0.136.1+1.21.8"; Variant = "modern" },
    @{ Version = "1.21.9"; Yarn = "1.21.9+build.1"; Api = "0.134.1+1.21.9"; Variant = "latest" },
    @{ Version = "1.21.10"; Yarn = "1.21.10+build.3"; Api = "0.138.4+1.21.10"; Variant = "latest" },
    @{ Version = "1.21.11"; Yarn = "1.21.11+build.6"; Api = "0.141.4+1.21.11"; Variant = "latest" }
)

$targets26 = @(
    @{ Version = "26.1"; Api = "0.145.1+26.1" },
    @{ Version = "26.1.1"; Api = "0.145.4+26.1.1" },
    @{ Version = "26.1.2"; Api = "0.152.1+26.1.2" }
)

function Copy-ModJar {
    param(
        [string] $BuildDirectory,
        [string] $Version
    )

    $jar = Get-ChildItem -LiteralPath $BuildDirectory -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*-sources.jar" } |
        Select-Object -First 1

    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $output "helpfularmorers-$Version-1.0.0.jar")
}

New-Item -ItemType Directory -Force -Path $output | Out-Null
Get-ChildItem -LiteralPath $output -Filter "*.jar" | Remove-Item -Force

foreach ($target in $targets21) {
    Write-Host "Building Minecraft $($target.Version)"
    $env:JAVA_HOME = $java21Home
    & $gradle -p $root clean build --no-daemon `
        "-Pminecraft_version=$($target.Version)" `
        "-Pyarn_mappings=$($target.Yarn)" `
        "-Pfabric_api_version=$($target.Api)" `
        "-Pmapping_variant=$($target.Variant)"

    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for Minecraft $($target.Version)"
    }

    Copy-ModJar (Join-Path $root "build\libs") $target.Version
}

$java25Home = if ($env:JAVA_25_HOME) { $env:JAVA_25_HOME } else { $bundledJava25 }
if (-not (Test-Path (Join-Path $java25Home "bin\java.exe"))) {
    throw "Java 25 was not found. Set JAVA_25_HOME before building Minecraft 26.1+."
}

foreach ($target in $targets26) {
    Write-Host "Building Minecraft $($target.Version)"
    $env:JAVA_HOME = $java25Home
    & $gradle -p (Join-Path $root "mc26") clean build --no-daemon `
        "-Pminecraft_version=$($target.Version)" `
        "-Pfabric_api_version=$($target.Api)"

    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for Minecraft $($target.Version)"
    }

    Copy-ModJar (Join-Path $root "mc26\build\libs") $target.Version
}

$env:JAVA_HOME = $java21Home
Write-Host "Created $($targets21.Count + $targets26.Count) jars in $output"
