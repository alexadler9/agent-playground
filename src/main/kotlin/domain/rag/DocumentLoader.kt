package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.readText

class DocumentLoader {

    fun load(root: Path): List<SourceDocument> {
        require(Files.exists(root)) {
            "Documents root does not exist: $root"
        }

        return Files.walk(root)
            .use { stream ->
                stream
                    .filter { path -> path.isRegularFile() }
                    .filter { path -> path.extension in supportedExtensions }
                    .map { path -> path.toSourceDocument(root) }
                    .toList()
            }
            .sortedBy { document -> document.source }
    }

    private fun Path.toSourceDocument(root: Path): SourceDocument {
        val relativePath = relativeTo(root).toString().replace("\\", "/")
        val text = readText(Charsets.UTF_8)

        return SourceDocument(
            source = relativePath,
            title = extractTitle(
                fileName = name,
                text = text,
                type = detectType(),
            ),
            type = detectType(),
            text = text,
        )
    }

    private fun Path.detectType(): SourceDocumentType {
        return when (extension.lowercase()) {
            "md" -> SourceDocumentType.MARKDOWN
            "txt" -> SourceDocumentType.TEXT
            "kt" -> SourceDocumentType.KOTLIN
            else -> error("Unsupported document type: $this")
        }
    }

    private fun extractTitle(
        fileName: String,
        text: String,
        type: SourceDocumentType,
    ): String {
        if (type == SourceDocumentType.MARKDOWN) {
            val firstHeading = text
                .lineSequence()
                .firstOrNull { line -> line.startsWith("# ") }
                ?.removePrefix("# ")
                ?.trim()

            if (!firstHeading.isNullOrBlank()) {
                return firstHeading
            }
        }

        return fileName.substringBeforeLast('.')
    }

    private companion object {
        val supportedExtensions = setOf("md", "txt", "kt")
    }
}