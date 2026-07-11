package data.local

import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApi {

    @POST("api/generate")
    suspend fun generate(
        @Body request: OllamaGenerateRequest,
    ): OllamaGenerateResponse
}