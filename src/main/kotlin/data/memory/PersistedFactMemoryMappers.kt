package data.memory

import data.memory.dto.PersistedFactMemoryDto
import domain.model.FactMemory

fun FactMemory.toPersistedDto(): PersistedFactMemoryDto {
    return PersistedFactMemoryDto(
        facts = facts,
    )
}

fun PersistedFactMemoryDto.toDomain(): FactMemory {
    return FactMemory(
        facts = facts,
    )
}