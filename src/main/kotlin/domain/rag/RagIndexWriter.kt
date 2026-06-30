package domain.rag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class RagIndexWriter(
    private val json: Json,
) {

    fun write(
        index: RagIndex,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = json.encodeToString(index),
            charset = Charsets.UTF_8,
        )
    }
}