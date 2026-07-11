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

Write-Host "Starting private AI service..."
Write-Host "Port: $port"
Write-Host "Model: $model"
Write-Host "Max messages: $env:PRIVATE_AI_MAX_MESSAGES"
Write-Host "Max requests: $env:PRIVATE_AI_MAX_REQUESTS / minute"
Write-Host ""

.\gradlew.bat --console=plain -q run --args="private-ai-service $port $model"