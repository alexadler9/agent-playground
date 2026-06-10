package presentation.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

class ProjectFilesStressTextBuilder(
    root: Path = Path.of("."),
) {

    private val absoluteRoot: Path = root
        .toAbsolutePath()
        .normalize()

    fun build(repeatCount: Int): String {
        val normalizedRepeatCount = repeatCount.coerceIn(1, MAX_REPEAT_COUNT)

        val files = Files.walk(absoluteRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.isAllowedProjectFile() }
                .sorted(Comparator.comparing { path ->
                    path.toAbsolutePath().normalize().toString()
                })
                .limit(MAX_FILES)
                .toList()
        }

        val singlePassText = buildString {
            appendLine("Analyze these project files.")
            appendLine("This is a stress-test payload for context window overflow.")
            appendLine()

            files.forEach { file ->
                val relativePath = absoluteRoot.relativize(
                    file.toAbsolutePath().normalize()
                )

                appendLine("===== FILE: $relativePath =====")
                appendLine(file.readText().take(MAX_CHARS_PER_FILE))
                appendLine()
            }
        }

        return buildString {
            repeat(normalizedRepeatCount) { index ->
                appendLine("===== STRESS PAYLOAD COPY ${index + 1} / $normalizedRepeatCount =====")
                appendLine(singlePassText)
                appendLine()
            }

            appendLine("Question: briefly summarize what these files are doing.")
        }
    }

    private fun Path.isAllowedProjectFile(): Boolean {
        val normalizedPath = toAbsolutePath().normalize()

        if (!normalizedPath.startsWith(absoluteRoot)) {
            return false
        }

        val relativePath = absoluteRoot.relativize(normalizedPath)

        val ignoredParts = setOf(
            ".git",
            ".gradle",
            ".idea",
            "build",
            "storage",
        )

        if (relativePath.any { part -> part.name in ignoredParts }) {
            return false
        }

        return extension.lowercase() in ALLOWED_EXTENSIONS
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf(
            "kt",
            "kts",
            "toml",
            "md",
            "txt",
        )

        const val MAX_FILES = 80L
        const val MAX_CHARS_PER_FILE = 50_000
        const val MAX_REPEAT_COUNT = 100
    }
}