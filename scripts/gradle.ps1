# Run the project's Gradle wrapper with a locally provisioned JDK when available.
# Remake by FelixMantra.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousPath = $env:PATH
$previousJavaHome = $env:JAVA_HOME

try {
    if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
        $localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk-17*' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
            Sort-Object Name -Descending | Select-Object -First 1
        if (-not $localJdk) {
            throw 'Install JDK 17 and set JAVA_HOME, or extract a JDK 17 archive into .tools/.'
        }
        $env:JAVA_HOME = $localJdk.FullName
    }
    if ($env:JAVA_HOME) {
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        $gitDirectory = Join-Path $env:ProgramFiles 'Git\cmd'
        if (Test-Path (Join-Path $gitDirectory 'git.exe')) {
            $env:PATH = $gitDirectory + ';' + $env:PATH
        }
    }
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') @args
        $buildExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:PATH = $previousPath
    $env:JAVA_HOME = $previousJavaHome
}
exit $buildExitCode
