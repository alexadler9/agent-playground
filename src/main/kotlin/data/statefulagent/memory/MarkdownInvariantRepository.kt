package data.statefulagent.memory

import domain.statefulagent.memory.InvariantRepository
import domain.statefulagent.model.InvariantSet
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class MarkdownInvariantRepository(
    private val storageFile: Path,
    private val resourcePath: String = "stateful-agent/invariants.md",
) : InvariantRepository {

    override suspend fun getInvariants(): InvariantSet {
        if (!storageFile.exists()) {
            return InvariantSet(
                content = "Инварианты не заданы",
            )
        }

        return InvariantSet(
            content = storageFile.readText(),
        )
    }
}