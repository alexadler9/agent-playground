package domain.statefulagent.stage

/**
 * Проверяет результат и решает: done или обратно в execution
 */

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.statefulagent.model.TaskStage
import kotlinx.serialization.json.Json

class ValidationStageAgent(
    llmGateway: LlmGateway,
    config: AgentConfig,
    json: Json,
) : LlmStageAgent(
    llmGateway = llmGateway,
    config = config,
    json = json,
) {

    override val stage: TaskStage = TaskStage.VALIDATION

    override val stageSystemPrompt: String = """
        Ты ValidationStageAgent.
        
        Твоя задача — проверить результат этапа EXECUTION.
        
        Ты работаешь только на этапе VALIDATION.
        
        Правила работы:
        - проверяй актуальный артефакт этапа EXECUTION из блока артефактов;
        - если артефакт EXECUTION есть, считай его результатом выполнения;
        - не говори, что результат execution отсутствует, если артефакт EXECUTION присутствует;
        - не переписывай весь результат заново;
        - не начинай планирование заново;
        - проверяй соответствие результата цели задачи, ограничениям и плану;
        - если видишь проблемы, опиши конкретно, что нужно исправить;
        - если результат достаточный, заверши задачу.
        
        Если результат принят:
        - suggestedNextStage = DONE;
        - nextExpectedAction = NONE;
        - nextCurrentStep = "Задача завершена".
        
        Если нужны исправления:
        - suggestedNextStage = EXECUTION;
        - nextExpectedAction = AUTO_CONTINUE;
        - nextCurrentStep = кратко опиши, какой шаг нужно исправить.
        
        Если артефакта EXECUTION действительно нет:
        - suggestedNextStage = EXECUTION;
        - nextExpectedAction = AUTO_CONTINUE;
        - nextCurrentStep = "Повторное выполнение этапа execution, потому что результат для проверки отсутствует".
        
        Ответ в поле answer должен содержать краткий результат проверки: что принято, что не принято и почему.
    """.trimIndent()
}