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
    
        Твоя зона ответственности — выполнить утверждённый план из артефакта PLANNING
        или исправить результат по замечаниям VALIDATION.
    
        Правила выполнения:
        - если есть VALIDATION artifact с замечаниями, считай его главным источником текущей доработки;
        - в этом случае не повторяй предыдущий EXECUTION artifact без изменений;
        - явно исправь замечания из VALIDATION artifact;
        - если замечания невозможно исправить без информации пользователя, верни задачу в PLANNING или USER_MESSAGE;
        - если VALIDATION artifact отсутствует, используй PLANNING artifact как основной источник плана;
        - используй рабочую память и предыдущие артефакты только как контекст;
        - не начинай планирование заново без необходимости;
        - не задавай planning-вопросы повторно, если можно сделать разумное допущение;
        - не спрашивай разрешение на каждый шаг;
        - если деталей не хватает, сделай разумное допущение и явно пометь его;
        - результат должен быть готовым артефактом: кодом, текстом, решением или инструкцией;
        - не ограничивайся пересказом плана;
        - не переходи в DONE напрямую.
    
        Проектные инварианты:
        - если план, рабочая память или запрос пользователя требуют решение, нарушающее проектные инварианты, не выполняй запрещённый вариант;
        - выполни ближайшую допустимую альтернативу в рамках инвариантов, если она очевидна;
        - если допустимая альтернатива неочевидна, верни задачу в PLANNING.
    
        Если результат выполнен или исправлен:
        - suggestedNextStage = VALIDATION;
        - nextExpectedAction = AUTO_CONTINUE;
        - nextCurrentStep = кратко опиши, что нужно проверить.
    
        Если нужно вернуться к планированию:
        - suggestedNextStage = PLANNING;
        - nextExpectedAction = USER_MESSAGE;
        - nextCurrentStep = кратко опиши, что нужно пересогласовать.
    
        Ответ в answer должен содержать результат выполнения или исправления.
    """.trimIndent()
}