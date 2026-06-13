package domain.memory

import domain.model.ChatMessage
import domain.model.ConversationBranch

class BranchManager {

    private val historiesByBranchId = mutableMapOf<String, MutableList<ChatMessage>>()

    var currentBranch: ConversationBranch = ConversationBranch(
        id = DEFAULT_BRANCH_ID,
        name = DEFAULT_BRANCH_ID,
    )
        private set

    init {
        historiesByBranchId[DEFAULT_BRANCH_ID] = mutableListOf()
    }

    fun getCurrentHistory(): List<ChatMessage> {
        return historiesByBranchId[currentBranch.id]
            ?.toList()
            .orEmpty()
    }

    fun replaceCurrentHistory(history: List<ChatMessage>) {
        historiesByBranchId[currentBranch.id] = history.toMutableList()
    }

    fun createBranch(name: String): ConversationBranch {
        val normalizedName = name.trim()

        require(normalizedName.isNotBlank()) {
            "Branch name must not be blank"
        }

        require(normalizedName !in historiesByBranchId.keys) {
            "Branch already exists: $normalizedName"
        }

        val branch = ConversationBranch(
            id = normalizedName,
            name = normalizedName,
        )

        historiesByBranchId[branch.id] = getCurrentHistory().toMutableList()

        return branch
    }

    fun switchBranch(name: String): ConversationBranch {
        val normalizedName = name.trim()

        require(normalizedName in historiesByBranchId.keys) {
            "Branch does not exist: $normalizedName"
        }

        currentBranch = ConversationBranch(
            id = normalizedName,
            name = normalizedName,
        )

        return currentBranch
    }

    fun listBranches(): List<ConversationBranch> {
        return historiesByBranchId.keys
            .sorted()
            .map { branchId ->
                ConversationBranch(
                    id = branchId,
                    name = branchId,
                )
            }
    }

    fun clearCurrentBranch() {
        historiesByBranchId[currentBranch.id] = mutableListOf()
    }

    private companion object {
        const val DEFAULT_BRANCH_ID = "main"
    }
}