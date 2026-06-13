package domain.context

class ContextBuilderProvider(
    initialStrategy: ContextStrategyType,
    private val fullHistoryContextBuilder: ContextBuilder,
    private val slidingWindowContextBuilder: ContextBuilder,
    private val stickyFactsContextBuilder: ContextBuilder,
) {

    var currentStrategy: ContextStrategyType = initialStrategy
        private set

    fun getCurrentBuilder(): ContextBuilder {
        return when (currentStrategy) {
            ContextStrategyType.FULL_HISTORY -> fullHistoryContextBuilder
            ContextStrategyType.SLIDING_WINDOW -> slidingWindowContextBuilder
            ContextStrategyType.STICKY_FACTS -> stickyFactsContextBuilder
        }
    }

    fun switchStrategy(strategy: ContextStrategyType) {
        currentStrategy = strategy
    }
}