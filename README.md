# Agent Playground

Kotlin/JVM CLI application with a simple AI agent that persists session history, tracks token usage, and compresses older conversation context into a summary

The agent stores user and assistant messages in a local JSON file, restores them after app restart, and builds LLM context from a summary of older messages plus recent raw messages

## Commands

```text
/history — show current session history
/clear — clear current session history
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

The storage/ directory is ignored by Git because it contains local runtime data

## Context compression

The agent keeps the full conversation history in storage, but does not send the entire history to the model on every request

Older messages are compressed into a summary. Newer messages are sent as raw messages

Current settings:

```text
recentMessagesCount = 10
summarizeBatchSize = 10
```

The context sent to the LLM is built as:

system prompt
+ summary of older messages
+ unsummarized recent messages

This reduces prompt size while preserving the main conversation context

## Token statistics and context stress-test

After each model response, the CLI prints:

* approximate token estimate for the current request
* approximate token estimate for stored history
* estimated full context size without compression
* estimated actual context size sent to the model
* estimated context savings
* real token usage returned by the API: prompt, completion, and total tokens

Token estimates are approximate. Exact prompt/completion/total usage is taken from the API response

The /stress-context [repeat] command sends project files as a large payload. It is used to observe how prompt tokens grow and what happens when the model context window is exceeded
