[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

$port = if ($args.Count -ge 1) { $args[0] } else { "8081" }
$model = if ($args.Count -ge 2) { $args[1] } else { "qwen2.5:3b" }

if (-not $env:PRIVATE_AI_TOKEN) {
    $env:PRIVATE_AI_TOKEN = "local-dev-token"
}

if (-not $env:PRIVATE_AI_MAX_MESSAGES) {
    $env:PRIVATE_AI_MAX_MESSAGES = "20"
}

if (-not $env:PRIVATE_AI_MAX_REQUESTS) {
    $env:PRIVATE_AI_MAX_REQUESTS = "10"
}

$installRoot = Join-Path $PSScriptRoot "build\install"

if (-not (Test-Path $installRoot)) {
    Write-Error "Distribution not found. Run: .\gradlew.bat --console=plain -q installDist"
    exit 1
}

$appDir = Get-ChildItem -Path $installRoot -Directory | Select-Object -First 1

if ($null -eq $appDir) {
    Write-Error "No application directory found in build\install"
    exit 1
}

$batFile = Get-ChildItem -Path (Join-Path $appDir.FullName "bin") -Filter "*.bat" | Select-Object -First 1

if ($null -eq $batFile) {
    Write-Error "No .bat launcher found in $($appDir.FullName)\bin"
    exit 1
}

Write-Host "Starting private AI service from distribution..."
Write-Host "App: $($appDir.Name)"
Write-Host "Launcher: $($batFile.FullName)"
Write-Host "Port: $port"
Write-Host "Model: $model"
Write-Host "Max messages: $env:PRIVATE_AI_MAX_MESSAGES"
Write-Host "Max requests: $env:PRIVATE_AI_MAX_REQUESTS / minute"
Write-Host ""

& $batFile.FullName "private-ai-service" $port $model