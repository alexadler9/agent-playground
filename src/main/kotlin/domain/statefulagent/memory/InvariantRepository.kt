package domain.statefulagent.memory

import domain.statefulagent.model.InvariantSet

interface InvariantRepository {

    suspend fun getInvariants(): InvariantSet
}