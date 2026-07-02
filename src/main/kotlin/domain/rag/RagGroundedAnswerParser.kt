package domain.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Достаёт структурированный JSON из ответа модели.
 *
 * Даже если модель случайно обернула JSON в markdown-блок,
 * пытаемся вытащить объект между первой "{" и последней "}".
 */
class RagGroundedAnswerParser(
    private val json: Json,
) {

    fun parse(rawResponse: String): RagGroundedLlmResponseDto {
        val jsonText = extractJsonObject(rawResponse)
        return json.decodeFromString<RagGroundedLlmResponseDto>(jsonText)
    }

    private fun extractJsonObject(rawResponse: String): String {
        val trimmed = rawResponse.trim()

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')

        require(start in 0..<end) {
            "LLM response does not contain a JSON object"
        }

        return trimmed.substring(start, end + 1)
    }
}