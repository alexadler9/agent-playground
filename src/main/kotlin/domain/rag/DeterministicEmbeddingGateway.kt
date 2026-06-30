package domain.rag

import kotlin.math.sqrt

class DeterministicEmbeddingGateway(
    private val vectorSize: Int = 64,
) : EmbeddingGateway {

    override val modelName: String = "deterministic-local-test-$vectorSize"

    override suspend fun embed(text: String): List<Float> {
        val vector = FloatArray(vectorSize)

        text
            .lowercase()
            .split(nonWordRegex)
            .filter { token -> token.isNotBlank() }
            .forEach { token ->
                val index = token.hashCode().floorMod(vectorSize)
                vector[index] += 1.0f
            }

        return vector.normalized()
    }

    private fun FloatArray.normalized(): List<Float> {
        val length = sqrt(sumOf { value -> (value * value).toDouble() })

        if (length == 0.0) {
            return toList()
        }

        return map { value -> (value / length).toFloat() }
    }

    private fun Int.floorMod(mod: Int): Int {
        return ((this % mod) + mod) % mod
    }

    private companion object {
        val nonWordRegex = Regex("""[^\p{L}\p{N}_]+""")
    }
}