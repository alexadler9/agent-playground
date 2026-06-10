# Agent Playground

Kotlin/JVM CLI application with a simple AI agent that persists session history between app runs and can show token usage statistics

The agent stores user and assistant messages in a local JSON file, restores them after app restart, and sends the session history to the LLM on each new request

## Commands

```text
/history — show current session history
/clear — clear current session history
/stress-context [repeat] — send project files as a large context payload
/exit — exit the app
```

## Environment variables

The API key is read from an environment variable:

For DeepSeek:
```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "your_api_key", "User")
```
For OpenRouter:
```powershell
[Environment]::SetEnvironmentVariable("OPENROUTER_API_KEY", "your_api_key", "User")
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
* Token estimates are approximate; exact usage is taken from the API response
  
## Persistent history

Session history is saved to:

```text
storage/session-history.json
```

The storage/ directory is ignored by Git because it contains local runtime data

After app restart, the agent loads the saved messages and continues the conversation with the previous context

## Token statistics and context stress-test

After each model response, the CLI prints:

approximate token estimates for the current request, stored history, and full context
real token usage returned by the API: prompt, completion, and total tokens

The /stress-context [repeat] command sends project files as a large payload. It is used to observe how prompt tokens grow and what happens when the model context window is exceeded

For context overflow experiments, the project can use OpenRouter with context compression disabled or enabled
