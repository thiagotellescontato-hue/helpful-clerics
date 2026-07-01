param(
    [Parameter(Mandatory = $true)]
    [string] $MinecraftVersion,

    [string] $ModVersion = "1.2.0",

    [string] $ProjectId = "LfvbzzA6",

    [ValidateSet("release", "beta", "alpha")]
    [string] $VersionType = "release"
)

$ErrorActionPreference = "Stop"

if (-not $env:MODRINTH_TOKEN) {
    throw "Set MODRINTH_TOKEN before running this script."
}

$jar = Join-Path $PSScriptRoot "jars\helpfulclerics-$MinecraftVersion-$ModVersion.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Jar not found: $jar"
}

$filePart = "mod-file"
$versionNumber = "$ModVersion+mc$MinecraftVersion"
$data = @{
    name = "Helpful Clerics $ModVersion for Minecraft $MinecraftVersion"
    version_number = $versionNumber
    changelog = "Helpful Clerics $ModVersion for Minecraft $MinecraftVersion."
    dependencies = @(
        @{
            project_id = "P7dR8mSH"
            dependency_type = "required"
        }
    )
    game_versions = @($MinecraftVersion)
    version_type = $VersionType
    loaders = @("fabric")
    featured = $false
    status = "listed"
    project_id = $ProjectId
    file_parts = @($filePart)
    primary_file = $filePart
} | ConvertTo-Json -Depth 5 -Compress

$tempData = Join-Path ([System.IO.Path]::GetTempPath()) "helpfulclerics-modrinth-$([guid]::NewGuid()).json"
$tempResponse = Join-Path ([System.IO.Path]::GetTempPath()) "helpfulclerics-modrinth-$([guid]::NewGuid()).response"

try {
    [System.IO.File]::WriteAllText($tempData, $data, [System.Text.UTF8Encoding]::new($false))

    $statusCode = & curl.exe --silent --show-error `
        --output $tempResponse `
        --write-out "%{http_code}" `
        --request POST "https://api.modrinth.com/v2/version" `
        --header "Authorization: $env:MODRINTH_TOKEN" `
        --header "User-Agent: thiagotellescontato-hue/helpful-clerics" `
        --form "data=@${tempData};type=application/json" `
        --form "${filePart}=@${jar};type=application/java-archive"

    $response = [System.IO.File]::ReadAllText($tempResponse)

    if ($LASTEXITCODE -ne 0 -or $statusCode -notmatch "^2") {
        try {
            $errorResponse = $response | ConvertFrom-Json
            throw "Modrinth upload failed ($statusCode): $($errorResponse.description)"
        } catch [System.Management.Automation.RuntimeException] {
            throw
        } catch {
            throw "Modrinth upload failed ($statusCode): $response"
        }
    }
} finally {
    Remove-Item -LiteralPath $tempData, $tempResponse -Force -ErrorAction SilentlyContinue
}

$result = $response | ConvertFrom-Json
Write-Host "Published $($result.version_number): https://modrinth.com/mod/helpful-clerics/version/$($result.id)"
