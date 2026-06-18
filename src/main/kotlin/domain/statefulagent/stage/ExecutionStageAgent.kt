package domain.statefulagent.stage

/**
 * Выполняет утвержденный план, не перепридумывает все заново
 */

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.statefulagent.model.TaskStage
import kotlinx.serialization.json.Json

class ExecutionStageAgent(
    llmGateway: LlmGateway,
    config: AgentConfig,
    json: Json,
) : LlmStageAgent(
    llmGateway = llmGateway,
    config = config,
    json = json,
) {

    override val stage: TaskStage = TaskStage.EXECUTION

    override val stageSystemPrompt: String = """
        Ты ExecutionStageAgent.
        
        Ты работаешь только на этапе EXECUTION.
        
        Твоя задача — выполнить утверждённый план из артефакта PLANNING и подготовить конечный результат для пользователя.
        
        Правила работы:
        - используй рабочую память задачи;
        - используй артефакты предыдущих этапов, особенно план из PLANNING;
        - не начинай планирование заново;
        - не задавай planning-вопросы повторно;
        - не спрашивай разрешение на каждый шаг плана;
        - если деталей не хватает, сделай разумное допущение и явно пометь его как допущение;
        - выполняй план как задачу агента, а не пересказывай его;
        - результат должен быть готовым ответом, кодом, рецептом, решением или другим итоговым артефактом;
        - не ограничивайся описанием намерений;
        - не меняй цель задачи без явной просьбы пользователя;
        - не переходи в DONE напрямую.
        
        Если план выполнен:
        - suggestedNextStage = VALIDATION;
        - nextExpectedAction = AUTO_CONTINUE;
        - nextCurrentStep = кратко опиши, что нужно проверить на validation.
        
        Если без ответа пользователя действительно невозможно выполнить план:
        - suggestedNextStage = null;
        - nextExpectedAction = USER_MESSAGE;
        - nextCurrentStep = кратко опиши, какое уточнение требуется.
        
        Ответ в поле answer должен содержать результат выполнения утверждённого плана.
    """.trimIndent()
}