package domain.antiprocrastination

import data.local.OllamaLocalLlmClient
import kotlin.system.measureTimeMillis

/**
 * Прогоняет один и тот же кейс через baseline и optimized настройки.
 *
 * Так мы сравниваем не разные задачи, а влияние prompt-шаблона
 * и параметров локальной модели на один и тот же вход.
 */
class AntiProcrastinatorOptimizationRunner(
    private val client: OllamaLocalLlmClient,
    private val model: String,
) {

    private val promptBuilder = AntiProcrastinatorPromptBuilder()

    suspend fun compare(
        request: AntiProcrastinatorRequest,
    ): AntiProcrastinatorComparisonResult {
        val baselineSettings = AntiProcrastinatorLlmSettings(
            mode = "baseline",
            temperature = 0.8,
            maxTokens = 1_000,
            promptName = "generic-help-prompt",
        )

        val optimizedSettings = buildOptimizedSettings()

        val baselinePrompt = promptBuilder.buildBaselinePrompt(request)
        val optimizedPrompt = promptBuilder.buildOptimizedPrompt(request)

        val baseline = runOne(
            settings = baselineSettings,
            prompt = baselinePrompt,
        )

        val optimized = runOne(
            settings = optimizedSettings,
            prompt = optimizedPrompt,
        )

        return AntiProcrastinatorComparisonResult(
            request = request,
            baseline = baseline,
            optimized = optimized,
        )
    }

    suspend fun compareBatch(
        requests: List<AntiProcrastinatorRequest>,
    ): AntiProcrastinatorBatchComparisonResult {
        return AntiProcrastinatorBatchComparisonResult(
            model = model,
            items = requests.map { request ->
                compare(request)
            },
        )
    }

    private suspend fun runOne(
        settings: AntiProcrastinatorLlmSettings,
        prompt: String,
    ): AntiProcrastinatorRunResult {
        var answer = ""
        var promptTokens: Int? = null
        var outputTokens: Int? = null

        val durationMs = measureTimeMillis {
            val response = client.ask(
                prompt = prompt,
                system = "Отвечай на русском языке.",
                temperature = settings.temperature,
                maxTokens = settings.maxTokens,
            )

            answer = response.response.trim()
            promptTokens = response.promptEvalCount
            outputTokens = response.evalCount
        }

        return AntiProcrastinatorRunResult(
            mode = settings.mode,
            model = model,
            settings = settings,
            prompt = prompt,
            answer = answer,
            durationMs = durationMs,
            promptTokens = promptTokens,
            outputTokens = outputTokens,
        )
    }

    private fun buildOptimizedSettings(): AntiProcrastinatorLlmSettings {
        return AntiProcrastinatorLlmSettings(
            mode = "optimized",
            temperature = 0.25,
            maxTokens = 350,
            promptName = "anti-procrastinator-compact-action-prompt",
        )
    }
}