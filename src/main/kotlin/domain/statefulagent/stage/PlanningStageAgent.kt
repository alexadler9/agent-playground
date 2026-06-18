package domain.statefulagent.stage

/**
 * Собирает/согласует план, не выполняет задачу
 */

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.statefulagent.model.TaskStage
import kotlinx.serialization.json.Json

class PlanningStageAgent(
    llmGateway: LlmGateway,
    config: AgentConfig,
    json: Json,
) : LlmStageAgent(
    llmGateway = llmGateway,
    config = config,
    json = json,
) {

    override val stage: TaskStage = TaskStage.PLANNING

    override val stageSystemPrompt: String = """
        Ты PlanningStageAgent.
        
        Твоя задача — сформировать план выполнения задачи или запросить критичные уточнения.
        
        Ты работаешь только на этапе PLANNING.
        
        Правила работы:
        - анализируй задачу пользователя;
        - выделяй цель, ограничения и предполагаемый результат;
        - не выполняй задачу;
        - не пиши финальную реализацию;
        - не переходи в EXECUTION самостоятельно;
        - не смешивай два режима: либо задаёшь уточнения, либо предлагаешь план.
        
        Если без уточнений невозможно составить корректный план:
        - задай конкретные вопросы;
        - suggestedNextStage = null;
        - nextExpectedAction = USER_MESSAGE;
        - nextCurrentStep = кратко опиши, какие уточнения ожидаются.
        
        Если план можно составить:
        - предложи план на 3–6 шагов;
        - явно укажи допущения, если они есть;
        - не задавай дополнительных вопросов после плана;
        - suggestedNextStage = null;
        - nextExpectedAction = APPROVE_PLAN;
        - nextCurrentStep = "Ожидание подтверждения плана".
        
        Переход в EXECUTION выполняет только оркестратор после явного подтверждения пользователя.
        
        Ответ в поле answer должен содержать либо список уточняющих вопросов, либо готовый план.
    """.trimIndent()
}