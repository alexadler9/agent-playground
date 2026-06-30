package domain.rag

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

class RagIndexReader(
    private val json: Json,
) {

    fun read(indexPath: Path): RagIndex {
        return json.decodeFromString(
            indexPath.readText(Charsets.UTF_8),
        )
    }
}