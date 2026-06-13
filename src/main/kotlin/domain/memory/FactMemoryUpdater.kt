package domain.memory

import domain.model.FactMemory

interface FactMemoryUpdater {

    suspend fun updateFacts(
        currentFacts: FactMemory,
        userMessage: String,
    ): FactMemory
}