package data.llm.api

import data.llm.dto.ChatCompletionRequestDto
import data.llm.dto.ChatCompletionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatCompletionApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequestDto,
    ): Response<ChatCompletionResponseDto>
}