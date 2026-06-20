package domain.statefulagent.stage

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import domain.statefulagent.stage.context.DefaultStageContextBuilder
import domain.statefulagent.stage.context.StageContextBuilder
import domain.statefulagent.stage.context.StageContextRequest
import kotlinx.serialization.json.Json

abstract class LlmStageAgent(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
    private val json: Json,
    private val stageContextBuilder: StageContextBuilder = DefaultStageContextBuilder(),
) : StageAgent {

    protected abstract val stageSystemPrompt: String

    override suspend fun handle(
        memory: AssistantMemory,
        taskState: TaskState,
        artifacts: Map<TaskStage, TaskArtifact>,
        invariants: InvariantSet,
        userMessage: String,
    ): StageAgentResult {
        val systemPrompt = stageContextBuilder.build(
            StageContextRequest(
                stage = stage,
                stageSystemPrompt = stageSystemPrompt,
                memory = memory,
                taskState = taskState,
                artifacts = artifacts,
                invariants = invariants,
            ),
        )

        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = systemPrompt,
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = userMessage,
            ),
        )

        val reply = llmGateway.sendMessages(
            messages = messages,
            config = config,
        )

        return parseResult(reply.message.content)
    }

    private fun parseResult(rawText: String): StageAgentResult {
        val cleanedText = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val dto = json.decodeFromString<StageAgentResponseDto>(cleanedText)

        return dto.toDomain()
    }
}