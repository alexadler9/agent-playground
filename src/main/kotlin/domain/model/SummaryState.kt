package domain.model

data class SummaryState(
    val content: String,
    val summarizedMessageCount: Int,
) {

    val hasSummary: Boolean
        get() = content.isNotBlank()

    companion object {

        val Empty = SummaryState(
            content = "",
            summarizedMessageCount = 0,
        )
    }
}