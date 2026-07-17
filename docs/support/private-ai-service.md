# Private AI service troubleshooting

This document describes common problems with the local private AI service.

## Service does not respond

If the private AI service does not respond, check the following:

1. The service process is actually running.
2. The local port is correct.
3. The request is sent to the correct endpoint.
4. Required environment variables are available to the process.
5. The API key is not only set in the terminal, but visible to the process that starts the service.

## DEEPSEEK_API_KEY

The `DEEPSEEK_API_KEY` environment variable is required when the service uses DeepSeek as the LLM provider.

If the key is missing, invalid, or unavailable to the running process, LLM requests will fail.

On Windows PowerShell, check the variable with:

```powershell
$env:DEEPSEEK_API_KEY
```

If the variable is empty, set it for the current session:

```powershell
$env:DEEPSEEK_API_KEY = "your-key"
```

If the service is started from another terminal, IDE, or GitHub Action, make sure the variable is configured there as well.

## Connection refused

`Connection refused` usually means that the client reached the host, but nothing is listening on the requested port.

This is different from an invalid API key.

For `Connection refused`, check service startup, port, host, and logs before debugging the API key.
