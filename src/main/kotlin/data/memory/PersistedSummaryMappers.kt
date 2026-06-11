package data.memory

import data.memory.dto.PersistedSummaryStateDto
import domain.model.SummaryState

fun SummaryState.toPersistedDto(): PersistedSummaryStateDto {
    return PersistedSummaryStateDto(
        content = content,
        summarizedMessageCount = summarizedMessageCount,
    )
}

fun PersistedSummaryStateDto.toDomain(): SummaryState {
    return SummaryState(
        content = content,
        summarizedMessageCount = summarizedMessageCount,
    )
}