package data.local

import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaChatApi {

    @POST("api/chat")
    suspend fun chat(
        @Body request: OllamaChatRequest,
    ): OllamaChatResponse
}