package domain.statefulagent.model

data class LongTermMemory(
    val profile: String = "",
    val decisions: String = "",
    val knowledge: String = "",
) {
    val isEmpty: Boolean
        get() = profile.isBlank() &&
                decisions.isBlank() &&
                knowledge.isBlank()

    companion object {
        val Empty = LongTermMemory()
    }
}