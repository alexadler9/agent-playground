package domain.statefulagent.model

import domain.model.ChatMessage

data class AssistantMemory(
    val shortTermMemory: List<ChatMessage>,
    val workingMemory: TaskContext,
    val longTermMemory: LongTermMemory,
)