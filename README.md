# Agent Playground

Kotlin/JVM CLI playground for experimenting with AI agent architecture, stateful task orchestration and local RAG

The project started as a context-management playground and now focuses on two main areas:

* **Stateful Agent** — an orchestrated assistant with explicit memory layers, task state and stage-based execution
* **RAG pipeline and RAG chat** — local document indexing, embeddings, retrieval, filtering/reranking, grounded answers with sources and quotes, and a production-like CLI chat with task memory

## Modes

### Stateful Agent

The stateful agent uses an explicit memory model with three separated memory layers:

* **Short-term memory** — current dialogue history
* **Working memory** — structured context of the current task
* **Long-term memory** — stable profile, decisions and reusable knowledge

The goal is to make it explicit what information is stored where and how it affects the assistant response

The stateful agent also includes a task state machine and stage-based orchestration. A task is represented through:

* current stage
* current step
* expected next action

Supported task stages:

* PLANNING
* EXECUTION
* VALIDATION
* DONE

The orchestration flow is controlled by Kotlin code. The LLM may suggest the next stage, but transitions are validated by the application

Typical flow:

```text
PLANNING -> EXECUTION -> VALIDATION -> DONE
```

Validation may return the task back to execution if the result is incomplete or does not satisfy the plan:

```text
VALIDATION -> EXECUTION -> VALIDATION
```

Run:

```powershell
.\gradlew.bat --console=plain -q run
```

### RAG indexing

The RAG indexing mode builds a local JSON index from documents

The pipeline:

```text
documents -> chunks -> embeddings -> JSON index
```

Supported source files:

* Markdown files (`.md`)
* Text files (`.txt`)
* Kotlin files (`.kt`)

Implemented chunking strategies:

* **Fixed-size chunking** — splits text into chunks of roughly equal size with overlap
* **Structure-aware chunking** — uses document structure where possible: Markdown headings, Kotlin declarations and text paragraphs

Implemented embedding providers:

* **deterministic** — local deterministic embedding for smoke tests and reproducible checks
* **ollama** — local Ollama embedding model, recommended for real RAG checks

Recommended multilingual model:

```powershell
ollama pull bge-m3
```

Build index:

```powershell
.\gradlew.bat --console=plain -q run --args="build-rag-index rag-documents rag-index ollama bge-m3"
```

Generated files:

```text
rag-index/fixed-size-index.json
rag-index/structure-index.json
```

`rag-documents/` and `rag-index/` are local working directories and should not be committed

### RAG chat

The RAG chat is a production-like CLI mode on top of the local RAG index

For every user message it:

* keeps the current dialogue history;
* updates task memory;
* rewrites the query for mixed Russian/English technical documents;
* searches the local RAG index;
* applies similarity filtering and heuristic reranking;
* generates a grounded answer;
* always prints sources;
* prints quotes from selected chunks;
* validates that quoted text exists in the selected chunks;
* returns "Don't know" when the retrieved context is too weak.

Task memory stores only the current dialogue state:

* user goal;
* user clarifications;
* constraints;
* important terms.

It does not store facts from the RAG documents. Factual claims should come from retrieved chunks.

Run:

```powershell
.\gradlew.bat --console=plain -q run --args="rag-chat rag-index\structure-index.json ollama bge-m3"
```

Chat commands:

```text
/memory — show current task memory
/exit  — exit the chat
```

## RAG retrieval details

The improved retrieval pipeline uses several steps:

```text
user message
-> task memory enriched retrieval query
-> query rewrite
-> vector search topKBefore
-> similarity threshold
-> relative score filtering
-> heuristic reranking
-> topKAfter chunks
-> grounded answer
```

The heuristic reranker combines:

* embedding similarity score;
* keyword matches in `source`, `title`, `section` and chunk text.

This keeps the implementation simple and deterministic while still making retrieval cleaner for mixed-language technical documents

## Grounding and anti-hallucination

Grounded RAG answers use a structured JSON contract internally:

```json
{
  "answer": "...",
  "sources": [
    {
      "source": "...",
      "section": "...",
      "chunk_id": "..."
    }
  ],
  "quotes": [
    {
      "source": "...",
      "section": "...",
      "chunk_id": "...",
      "text": "exact quote from chunk"
    }
  ]
}
```

The application validates:

* sources are present;
* quotes are present;
* referenced chunks were actually selected by retrieval;
* quote text is an exact fragment of the selected chunk text.

If the selected context is missing or below the similarity threshold, the chat does not produce a confident answer and returns an "I do not know" style response.

## Project invariants

Stateful Agent supports project invariants: stable constraints that are stored separately from the dialogue and must be considered by the agent when planning or proposing solutions

Invariants are used for things like:

* selected architecture;
* accepted technical decisions;
* stack constraints;
* project-level rules.

The agent receives invariants as a separate context block and must not suggest solutions that violate them. If the user asks for a conflicting solution, the agent should explain the conflict and suggest an allowed alternative

Invariants are stored outside the dialogue history:

```text
storage/stateful-agent/invariants.md
```

Example:

```text
User: Implement the mapper in Java.

Agent: Java conflicts with the project invariants because new components should be implemented in Kotlin. I can implement the mapper in Kotlin instead.
```

This means a conflicting user request should not become an active task constraint such as “use Java”. The agent should keep the project invariant as the higher-priority rule and continue with a valid alternative if the user agrees

## Stage agents

Each task stage is handled by a separate stage agent with its own system prompt:

* PlanningStageAgent — creates a plan or asks for missing information
* ExecutionStageAgent — performs the approved plan
* ValidationStageAgent — checks the execution result against the task, plan and constraints

The orchestrator selects the correct stage agent based on the persisted task state

## Task artifacts

Stage results are stored as task artifacts

Artifacts are separated from task state. The task state only describes where the task currently is, while artifacts store the latest meaningful result of each stage

Example artifacts:

* planning artifact — approved or pending plan
* execution artifact — produced solution or implementation
* validation artifact — validation result

Storage:

```text
storage/stateful-agent/task-artifacts.json
```

## Personalization

Personalization is implemented through markdown-based user profiles

Profiles are stored as free-form .md files. The user can create, edit and switch profiles without changing Kotlin code

Example profiles:

```text
storage/stateful-agent/profiles/default.md
storage/stateful-agent/profiles/android-dev.md
storage/stateful-agent/profiles/beginner.md
storage/stateful-agent/profiles/product-manager.md
```

The active profile is stored in:

```text
storage/stateful-agent/active-profile.txt
```

The active profile is loaded on every request and added to the long-term memory block

This allows the same user request to produce different answers depending on the selected profile

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
storage/stateful-agent/profiles/
storage/stateful-agent/active-profile.txt
storage/stateful-agent/long-term-memory/decisions.md
storage/stateful-agent/long-term-memory/knowledge.md
```

Long-term memory is edited manually through Markdown files

## Stateful Agent commands

```text
/memory [all|short|work|long]              — show memory layers
/profile [current|list|show|switch <name>] — manage user profiles
/clear [short|work|task|all]               — clear memory and task state
/exit — exit the app
```

## Environment variables

The API key is read from an environment variable:

```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "your_api_key", "User")
```

Restart the terminal or IDE after setting the variable

## Run commands

Build the project:

```powershell
.\gradlew.bat --console=plain -q build
```

Run Stateful Agent:

```powershell
.\gradlew.bat --console=plain -q run
```

Build RAG index:

```powershell
.\gradlew.bat --console=plain -q run --args="build-rag-index rag-documents rag-index ollama bge-m3"
```

Run RAG chat:

```powershell
.\gradlew.bat --console=plain -q run --args="rag-chat rag-index\structure-index.json ollama bge-m3"
```

## Persistent storage

Runtime files are stored in the `storage/` directory

The `storage/` directory is ignored by Git because it contains local runtime data

Current storage files include:

```text
storage/session-history.json
storage/session-summary.json
storage/facts.json
storage/stateful-agent/invariants.md
storage/stateful-agent/task-context.json
storage/stateful-agent/task-state.json
storage/stateful-agent/task-artifacts.json
storage/stateful-agent/active-profile.txt
storage/stateful-agent/profiles/default.md
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
* RAG chat history and task memory are stored only in the current CLI process
* RAG index is stored as local JSON, not as a production vector database
* RAG retrieval uses embedding similarity plus heuristic filtering/reranking
* Grounding validation checks quotes and sources, but it does not fully prove that the answer meaning is correct
* Working memory extraction depends on the LLM response
* Long-term memory is edited manually through Markdown files
* User profiles are file-based and switched through `active-profile.txt`
* Task orchestration is stage-based, but still depends on LLM output quality inside each stage
* Task artifacts currently store the latest artifact per stage, not a full versioned event log
