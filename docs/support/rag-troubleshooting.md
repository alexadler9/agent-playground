# RAG troubleshooting

This document describes common problems with the local RAG pipeline.

## RAG chat answers "Don't know"

If RAG chat answers `Don't know`, check the following:

1. The index was built from the expected documents.
2. The chat command points to the same index file that was generated.
3. The embedding provider is running.
4. The embedding model used for chat matches the model used for indexing.
5. The question is specific enough to match the indexed content.

For Ollama embeddings, make sure Ollama is running and the model is pulled locally.

Recommended model:

```bash
ollama pull bge-m3
```

## Wrong index file

The project may contain different index files for different chunking strategies.

If the index was built with structure-aware chunking, but chat is started with another index file, retrieval quality may be worse or the answer may not be grounded in the expected document.

Use the index file produced by the same indexing command.

## Grounded answers and quotes

The RAG chat is expected to answer only when it has enough context from retrieved chunks.

If quote validation is enabled, the answer should use quotes that are actually present in the retrieved source chunks.

If the model gives a plausible answer but cannot ground it in retrieved context, the assistant should refuse or say that the answer is not available in the indexed documents.
