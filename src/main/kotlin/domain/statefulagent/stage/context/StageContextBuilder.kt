package domain.statefulagent.stage.context

/**
 * Собирает system prompt для stage-agent-а.
 *
 * Отвечает за то, какой срез памяти, артефактов и инвариантов
 * увидит конкретный агент.
 */
interface StageContextBuilder {

    fun build(request: StageContextRequest): String
}