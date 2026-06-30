package domain.rag

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Читает сохранённый JSON-index обратно в доменную модель.
 *
 * Это отдельный класс, чтобы search-режим не зависел от деталей хранения файла.
 */
class RagIndexReader(
    private val json: Json,
) {

    fun read(indexPath: Path): RagIndex {
        return json.decodeFromString(
            indexPath.readText(Charsets.UTF_8),
        )
    }
}