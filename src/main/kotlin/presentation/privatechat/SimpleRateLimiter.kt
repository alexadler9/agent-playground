package presentation.privatechat

class SimpleRateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
) {

    private val requestsByClient = mutableMapOf<String, MutableList<Long>>()

    @Synchronized
    fun allow(
        clientKey: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        val requests = requestsByClient.getOrPut(clientKey) {
            mutableListOf()
        }

        requests.removeAll { timestamp ->
            timestamp < windowStart
        }

        if (requests.size >= maxRequests) {
            return false
        }

        requests += now
        return true
    }
}