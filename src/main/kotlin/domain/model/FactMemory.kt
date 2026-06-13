package domain.model

data class FactMemory(
    val facts: Map<String, String>,
) {

    val isEmpty: Boolean
        get() = facts.isEmpty()

    companion object {
        val Empty = FactMemory(
            facts = emptyMap(),
        )
    }
}