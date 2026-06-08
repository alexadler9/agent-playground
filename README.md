# Agent Playground

Kotlin/JVM CLI application with a simple session-memory AI agent

The agent stores user and assistant messages during the current app run and sends this session history to the LLM on each new request

## Commands

```text
/history — show current session history
/clear   — clear current session history
/exit    — exit the app
```

## Environment variables

The API key is read from an environment variable:

```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "your_api_key", "User")
```

Restart the terminal or IDE after setting the variable

## Run

```powershell
.\gradlew.bat run
```

or:

```bash
./gradlew run
```

## Current limitations

* History is stored only in memory
* Session memory is lost after app restart
* Long-term memory is not implemented yet
* CLI is the only presentation layer for now
* The current context builder sends the full session history
