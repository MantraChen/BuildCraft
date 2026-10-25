# Build the complete mod, then load the actual jar in a real Fabric GameTest world.
# Remake by FelixMantra.
param([switch]$Client, [switch]$Interactions, [switch]$Quarry, [switch]$MiningWell, [switch]$Pump)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'gradle.ps1') remapJar --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$artifact = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'build\libs') -Filter 'buildcraft-*.jar' |
    Where-Object { $_.Name -notmatch '-sources|-dev' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $artifact) { throw 'The complete BuildCraft jar was not produced.' }
$verification = Join-Path $taskRoot 'verification'
New-Item -ItemType Directory -Path $verification -Force | Out-Null
Copy-Item -LiteralPath $artifact.FullName -Destination (Join-Path $verification 'buildcraft-under-test.jar') -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nestedDirectory = Join-Path $verification 'full-test-libs'
New-Item -ItemType Directory -Path $nestedDirectory -Force | Out-Null
$packedJar = [IO.Compression.ZipFile]::OpenRead($artifact.FullName)
try {
    foreach ($entry in $packedJar.Entries) {
        if ($entry.FullName -like 'META-INF/jars/*.jar') {
            $destination = Join-Path $nestedDirectory ([IO.Path]::GetFileName($entry.FullName))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
        }
    }
} finally { $packedJar.Dispose() }
$testConfigDirectory = Join-Path $verification 'full-gametest-run\config\buildcraft'
New-Item -ItemType Directory -Path $testConfigDirectory -Force | Out-Null
$testConfigFile = Join-Path $testConfigDirectory 'main.cfg'
if (-not (Test-Path -LiteralPath $testConfigFile)) {
    @'
general {
    B:"enableMjDynamo"=true
    B:"enableRfEngine"=true
}
'@ | Set-Content -LiteralPath $testConfigFile -Encoding utf8
} else {
    $testConfig = Get-Content -LiteralPath $testConfigFile -Raw
    $testConfig = $testConfig -replace '(B:"enable(?:MjDynamo|RfEngine)"=)false', '${1}true'
    Set-Content -LiteralPath $testConfigFile -Value $testConfig -Encoding utf8
}
& (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runGameTest --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Client) {
    $clientRun = Join-Path $verification 'full-client-run'
    $clientConfig = Join-Path $clientRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $clientConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $clientConfig 'main.cfg') -Force
    $clientOptions = Join-Path $clientRun 'options.txt'
    if (-not (Test-Path -LiteralPath $clientOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $clientOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runFullClientSmoke --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($Interactions) {
    $interactionRun = Join-Path $verification 'full-client-interaction-run'
    New-Item -ItemType Directory -Path $interactionRun -Force | Out-Null
    $interactionConfig = Join-Path $interactionRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $interactionConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $interactionConfig 'main.cfg') -Force
    $interactionOptions = Join-Path $interactionRun 'options.txt'
    if (-not (Test-Path -LiteralPath $interactionOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $interactionOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runFullClientInteractionSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Quarry) {
    $quarryRun = Join-Path $verification 'quarry-client-run'
    $quarryConfig = Join-Path $quarryRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $quarryConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $quarryConfig 'main.cfg') -Force
    $quarryOptions = Join-Path $quarryRun 'options.txt'
    if (-not (Test-Path -LiteralPath $quarryOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $quarryOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runQuarryClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($MiningWell) {
    $wellRun = Join-Path $verification 'mining-well-client-run'
    $wellConfig = Join-Path $wellRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $wellConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $wellConfig 'main.cfg') -Force
    $wellOptions = Join-Path $wellRun 'options.txt'
    if (-not (Test-Path -LiteralPath $wellOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $wellOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runMiningWellClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Pump) {
    $pumpRun = Join-Path $verification 'pump-client-run'
    $pumpConfig = Join-Path $pumpRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $pumpConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $pumpConfig 'main.cfg') -Force
    $pumpOptions = Join-Path $pumpRun 'options.txt'
    if (-not (Test-Path -LiteralPath $pumpOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $pumpOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runPumpClientSmoke --console=plain
}
exit $LASTEXITCODE
