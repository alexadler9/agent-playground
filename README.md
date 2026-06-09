# Agent Playground

Kotlin/JVM CLI application with a simple AI agent that persists session history between app runs

The agent stores user and assistant messages in a local JSON file, restores them after app restart, and sends the session history to the LLM on each new request

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

* History is stored in a local JSON file
* Only one persisted session is supported for now
* Long-term structured memory is not implemented yet
* CLI is the only presentation layer for now
* The current context builder sends the full session history

## Persistent history

Session history is saved to:

```text
storage/session-history.json
```

The storage/ directory is ignored by Git because it contains local runtime data

After app restart, the agent loads the saved messages and continues the conversation with the previous context
