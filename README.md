# Agent Playground

Kotlin/JVM CLI application with a simple AI agent that persists session history, tracks token usage, and supports multiple context management strategies

The agent stores user and assistant messages locally, restores them after app restart, and builds LLM context using the selected strategy

## Commands

```text
/history — show current session history
/clear — clear current branch, facts and summary
/strategy [current|full|sliding|facts] — switch context strategy
/branch [current|list|create <name>|switch <name>] — manage conversation branches
/stress-context [repeat] — send project files as a large context payload
/exit — exit the app
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

* History and summary are stored in local JSON files
* Only one persisted session is supported for now
* Long-term structured memory is not implemented yet
* CLI is the only presentation layer for now
* Summary quality depends on the LLM response
* The current compression strategy is simple batch-based summarization

## Persistent storage

Session history is saved to:

```text
storage/session-history.json
```

Compressed summary is saved to:

```text
storage/session-summary.json
```

Sticky facts are saved to:

```text
storage/facts.json
```

The storage/ directory is ignored by Git because it contains local runtime data

## Context strategies
### Full History

Sends the full conversation history to the model

Useful as a baseline for comparison, but expensive for long conversations

### Sliding Window

Sends only the last N messages

This keeps context small, but older important details may disappear from the model context

### Sticky Facts

Stores stable facts separately as key-value memory and sends:

system prompt
+ facts
+ last N messages

This helps preserve important details even when old messages fall out of the recent window

### Branching

Allows creating independent conversation branches from a checkpoint

Each branch can continue with its own history, so different solution paths do not mix

## Token statistics and context stress-test

After each model response, the CLI prints compact state and token statistics:

```text
State: strategy=Sticky Facts, branch=main
Context: 1200 tokens, saved: 800, contextMessages=12, historyMessages=28
API: prompt=1300, completion=200, total=1500
```

Token estimates are approximate. Exact prompt/completion/total usage is taken from the API response

The /stress-context [repeat] command sends project files as a large payload. It is used to observe how prompt tokens grow and what happens when the model context window is exceeded
