# Agent Playground

Kotlin/JVM CLI playground for experimenting with AI agent architecture

The project started as a context-management playground and now includes a stateful agent with explicit memory layers

## Modes

The project contains two experimental agent modes

### Context Agent

The context agent is used to experiment with different ways of building LLM context

Supported context strategies

* **Full History** — sends the full conversation history
* **Sliding Window** — sends only the last N messages
* **Sticky Facts** — sends extracted key facts plus recent messages
* **Branching** — allows independent conversation branches from a checkpoint

This mode was used to compare context size, information loss, token usage and branch isolation

### Stateful Agent

The stateful agent uses an explicit memory model with three separated memory layers:

* **Short-term memory** — current dialogue history
* **Working memory** — structured context of the current task
* **Long-term memory** — stable profile, decisions and reusable knowledge

The goal is to make it explicit what information is stored where and how it affects the assistant response

## Memory layers

### Short-term memory

Short-term memory stores the current dialogue as user and assistant messages

It answers the question:

```text
What happened in the current conversation?
```

Storage:

```text
storage/session-history.json
```

### Working memory

Working memory stores structured information about the current task

It may include:

* task name
* goal
* current step
* completed items
* pending items
* task decisions
* task constraints

It answers the question:

```text
What task are we working on and what is its current state?
```

Storage:

```text
storage/stateful-agent/task-context.json
```

Working memory is updated separately from chat history through a `TaskContextUpdater`

### Long-term memory

Long-term memory stores stable information that should survive between tasks and sessions

It includes:

* user profile
* stable project decisions
* reusable knowledge

It answers the question:

```text
What should the assistant remember beyond the current task?
```

Storage:

```text
storage/stateful-agent/long-term-memory/profile.md
storage/stateful-agent/long-term-memory/decisions.md
storage/stateful-agent/long-term-memory/knowledge.md
```

Long-term memory is edited manually through Markdown files

## Stateful Agent commands

```text
/memory [all|short|work|long] — show memory layers
/clear-short                  — clear short-term memory
/clear-work                   — clear working memory
/exit                         — exit the app
```

## Context Agent commands

```text
/history                                      — show current session history
/clear                                        — clear current branch, facts and summary
/strategy [current|full|sliding|facts]         — switch context strategy
/branch [current|list|create <name>|switch <name>] — manage conversation branches
/stress-context [repeat]                      — send project files as a large context payload
/exit                                         — exit the app
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

## Persistent storage

Runtime files are stored in the `storage/` directory

The `storage/` directory is ignored by Git because it contains local runtime data

Current storage files include:

```text
storage/session-history.json
storage/session-summary.json
storage/facts.json
storage/stateful-agent/task-context.json
storage/stateful-agent/long-term-memory/profile.md
storage/stateful-agent/long-term-memory/decisions.md
storage/stateful-agent/long-term-memory/knowledge.md
```

## Token statistics

After model responses, the CLI may print compact token statistics:

```text
API: prompt=1300, completion=200, total=1500
```

Some modes also print estimated context statistics. Token estimates are approximate; exact prompt, completion and total usage are taken from the API response

## Current limitations

* CLI is the only presentation layer
* Runtime storage is local and file-based
* Only one main persisted session is supported
* Branches are stored in memory only
* Working memory extraction depends on the LLM response
* Long-term memory is edited manually through Markdown files
* Context compression and sticky facts are experimental strategies from the context-management mode
