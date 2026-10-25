# Build the complete mod, then load the actual jar in a real Fabric GameTest world.
# Remake by FelixMantra.
param([switch]$Client, [switch]$Interactions, [switch]$Quarry, [switch]$MiningWell, [switch]$Pump, [switch]$AutoCraft, [switch]$Distiller, [switch]$HeatExchange, [switch]$OilGeneration, [switch]$Assembly, [switch]$AdvancedCrafting, [switch]$Integration, [switch]$ControlPlugs, [switch]$TransportPlugs, [switch]$Lenses, [switch]$Facades, [switch]$GateCopier, [switch]$FloodGate, [switch]$Obsidian, [switch]$Stripes, [switch]$Filler, [switch]$Architect, [switch]$Builder, [switch]$Entities, [switch]$Replacer, [switch]$Library, [switch]$Planner)

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
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($AutoCraft) {
    $craftRun = Join-Path $verification 'autocraft-client-run'
    $craftConfig = Join-Path $craftRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $craftConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $craftConfig 'main.cfg') -Force
    $craftOptions = Join-Path $craftRun 'options.txt'
    if (-not (Test-Path -LiteralPath $craftOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $craftOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runAutoCraftClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Distiller) {
    $distillerRun = Join-Path $verification 'distiller-client-run'
    $distillerConfig = Join-Path $distillerRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $distillerConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $distillerConfig 'main.cfg') -Force
    $distillerOptions = Join-Path $distillerRun 'options.txt'
    if (-not (Test-Path -LiteralPath $distillerOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $distillerOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runDistillerClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($HeatExchange) {
    $heatRun = Join-Path $verification 'heat-exchange-client-run'
    $heatConfig = Join-Path $heatRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $heatConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $heatConfig 'main.cfg') -Force
    $heatOptions = Join-Path $heatRun 'options.txt'
    if (-not (Test-Path -LiteralPath $heatOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $heatOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runHeatExchangeClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($OilGeneration) {
    $oilRun = Join-Path $verification 'oil-generation-client-run'
    $oilConfig = Join-Path $oilRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $oilConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $oilConfig 'main.cfg') -Force
    $oilOptions = Join-Path $oilRun 'options.txt'
    if (-not (Test-Path -LiteralPath $oilOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $oilOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runOilGenerationClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Assembly) {
    $assemblyRun = Join-Path $verification 'assembly-client-run'
    $assemblyConfig = Join-Path $assemblyRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $assemblyConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $assemblyConfig 'main.cfg') -Force
    $assemblyOptions = Join-Path $assemblyRun 'options.txt'
    if (-not (Test-Path -LiteralPath $assemblyOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $assemblyOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runAssemblyClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($AdvancedCrafting) {
    $advancedRun = Join-Path $verification 'advanced-crafting-client-run'
    $advancedConfig = Join-Path $advancedRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $advancedConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $advancedConfig 'main.cfg') -Force
    $advancedOptions = Join-Path $advancedRun 'options.txt'
    if (-not (Test-Path -LiteralPath $advancedOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $advancedOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runAdvancedCraftingClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Integration) {
    $integrationRun = Join-Path $verification 'integration-client-run'
    $integrationConfig = Join-Path $integrationRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $integrationConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $integrationConfig 'main.cfg') -Force
    $integrationOptions = Join-Path $integrationRun 'options.txt'
    if (-not (Test-Path -LiteralPath $integrationOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $integrationOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runIntegrationClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($ControlPlugs) {
    $controlRun = Join-Path $verification 'control-plugs-client-run'
    $controlConfig = Join-Path $controlRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $controlConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $controlConfig 'main.cfg') -Force
    $controlOptions = Join-Path $controlRun 'options.txt'
    if (-not (Test-Path -LiteralPath $controlOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $controlOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runControlPlugClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($TransportPlugs) {
    $transportPlugRun = Join-Path $verification 'transport-plugs-client-run'
    $transportPlugConfig = Join-Path $transportPlugRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $transportPlugConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $transportPlugConfig 'main.cfg') -Force
    $transportSettingsPath = Join-Path $transportPlugConfig 'main.cfg'
    $transportSettings = Get-Content -LiteralPath $transportSettingsPath -Raw
    $transportSettings = $transportSettings -replace 'S:"powerMode"="[A-Z_]+"', 'S:"powerMode"="MJ_AUTOCONVERT_RF"'
    $transportSettings = $transportSettings -replace 'I:"pipes.mjPerItem"=\d+', 'I:"pipes.mjPerItem"=150000'
    Set-Content -LiteralPath $transportSettingsPath -Value $transportSettings -Encoding utf8
    $transportPlugOptions = Join-Path $transportPlugRun 'options.txt'
    if (-not (Test-Path -LiteralPath $transportPlugOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $transportPlugOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runTransportPlugClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Lenses) {
    $lensRun = Join-Path $verification 'lenses-client-run'
    $lensConfig = Join-Path $lensRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $lensConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $lensConfig 'main.cfg') -Force
    $lensOptions = Join-Path $lensRun 'options.txt'
    if (-not (Test-Path -LiteralPath $lensOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $lensOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runLensClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Facades) {
    $facadeRun = Join-Path $verification 'facades-client-run'
    $facadeConfig = Join-Path $facadeRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $facadeConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $facadeConfig 'main.cfg') -Force
    $facadeOptions = Join-Path $facadeRun 'options.txt'
    if (-not (Test-Path -LiteralPath $facadeOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $facadeOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runFacadeClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($GateCopier) {
    $copierRun = Join-Path $verification 'gate-copier-client-run'
    $copierConfig = Join-Path $copierRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $copierConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $copierConfig 'main.cfg') -Force
    $copierOptions = Join-Path $copierRun 'options.txt'
    if (-not (Test-Path -LiteralPath $copierOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $copierOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runGateCopierClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($FloodGate) {
    $floodRun = Join-Path $verification 'flood-gate-client-run'
    $floodConfig = Join-Path $floodRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $floodConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $floodConfig 'main.cfg') -Force
    $floodOptions = Join-Path $floodRun 'options.txt'
    if (-not (Test-Path -LiteralPath $floodOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $floodOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runFloodGateClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Obsidian) {
    $obsidianRun = Join-Path $verification 'obsidian-client-run'
    $obsidianConfig = Join-Path $obsidianRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $obsidianConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $obsidianConfig 'main.cfg') -Force
    $obsidianOptions = Join-Path $obsidianRun 'options.txt'
    if (-not (Test-Path -LiteralPath $obsidianOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $obsidianOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runObsidianClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Stripes) {
    $stripesRun = Join-Path $verification 'stripes-client-run'
    $stripesConfig = Join-Path $stripesRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $stripesConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $stripesConfig 'main.cfg') -Force
    $stripesOptions = Join-Path $stripesRun 'options.txt'
    if (-not (Test-Path -LiteralPath $stripesOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $stripesOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runStripesClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Filler) {
    $fillerRun = Join-Path $verification 'filler-client-run'
    $fillerConfig = Join-Path $fillerRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $fillerConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $fillerConfig 'main.cfg') -Force
    $fillerOptions = Join-Path $fillerRun 'options.txt'
    if (-not (Test-Path -LiteralPath $fillerOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $fillerOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runFillerClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Architect) {
    $architectRun = Join-Path $verification 'architect-client-run'
    $architectConfig = Join-Path $architectRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $architectConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $architectConfig 'main.cfg') -Force
    $architectOptions = Join-Path $architectRun 'options.txt'
    if (-not (Test-Path -LiteralPath $architectOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $architectOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runArchitectClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Builder) {
    $builderRun = Join-Path $verification 'builder-client-run'
    $builderConfig = Join-Path $builderRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $builderConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $builderConfig 'main.cfg') -Force
    $builderOptions = Join-Path $builderRun 'options.txt'
    if (-not (Test-Path -LiteralPath $builderOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $builderOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runBuilderClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Entities) {
    $entityRun = Join-Path $verification 'entity-blueprint-client-run'
    $entityConfig = Join-Path $entityRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $entityConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $entityConfig 'main.cfg') -Force
    $entityOptions = Join-Path $entityRun 'options.txt'
    if (-not (Test-Path -LiteralPath $entityOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $entityOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runEntityBlueprintClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Replacer) {
    $replacerRun = Join-Path $verification 'replacer-client-run'
    $replacerConfig = Join-Path $replacerRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $replacerConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $replacerConfig 'main.cfg') -Force
    $replacerOptions = Join-Path $replacerRun 'options.txt'
    if (-not (Test-Path -LiteralPath $replacerOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $replacerOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runReplacerClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Library) {
    $libraryRun = Join-Path $verification 'library-client-run'
    $libraryConfig = Join-Path $libraryRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $libraryConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $libraryConfig 'main.cfg') -Force
    $libraryOptions = Join-Path $libraryRun 'options.txt'
    if (-not (Test-Path -LiteralPath $libraryOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $libraryOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runLibraryClientSmoke --console=plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Planner) {
    $plannerRun = Join-Path $verification 'planner-client-run'
    $plannerConfig = Join-Path $plannerRun 'config\buildcraft'
    New-Item -ItemType Directory -Path $plannerConfig -Force | Out-Null
    Copy-Item -LiteralPath $testConfigFile -Destination (Join-Path $plannerConfig 'main.cfg') -Force
    $plannerOptions = Join-Path $plannerRun 'options.txt'
    if (-not (Test-Path -LiteralPath $plannerOptions)) {
        'onboardAccessibility:false' | Set-Content -LiteralPath $plannerOptions -Encoding utf8
    }
    & (Join-Path $PSScriptRoot 'gradle.ps1') -p sub_projects/full-tests runPlannerClientSmoke --console=plain
}
exit $LASTEXITCODE
