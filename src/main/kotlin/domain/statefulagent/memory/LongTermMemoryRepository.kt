package domain.statefulagent.memory

import domain.statefulagent.model.LongTermMemory

interface LongTermMemoryRepository {

    suspend fun getLongTermMemory(): LongTermMemory
}