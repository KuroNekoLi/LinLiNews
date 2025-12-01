package com.linli.news.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

@Service
class OpenAiEmbeddingService(
    @param:Value("\${openai.api-key:}") private val apiKey: String,
    @param:Value("\${openai.base-url:https://api.openai.com/v1}") private val baseUrl: String,
    @param:Value("\${openai.embedding-model:text-embedding-3-small}") private val defaultModel: String,
) {
    private val logger = LoggerFactory.getLogger(OpenAiEmbeddingService::class.java)

    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer $apiKey")
        .defaultHeader("User-Agent", "LinLiNews/1.0 (+SpringBoot)")
        .build()

    data class EmbeddingRequest(val model: String, val input: String)
    data class EmbeddingResponse(val data: List<EmbeddingData>)
    data class EmbeddingData(val embedding: List<Double>)

    fun embed(text: String, model: String = defaultModel): List<Double> {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        val body = EmbeddingRequest(model = model, input = text)
        var attempt = 0
        val maxAttempts = 3
        var delayMs = 1_000L
        while (true) {
            try {
                val response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse::class.java)
                    ?: throw IllegalStateException("Embedding API returned null body")

                val embedding = response.data.firstOrNull()?.embedding
                    ?: throw IllegalStateException("Embedding API response missing data")
                logger.debug("Generated embedding with dimension={} using model={}", embedding.size, model)
                return embedding
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode.value() == 429 && attempt++ < maxAttempts) {
                    logger.warn("Embedding API 429, retrying in ${delayMs}ms (attempt $attempt/$maxAttempts)")
                    Thread.sleep(delayMs)
                    delayMs *= 2
                    continue
                }
                throw ex
            } catch (ex: HttpServerErrorException) {
                if (attempt++ < maxAttempts) {
                    logger.warn("Embedding API ${ex.statusCode}, retrying in ${delayMs}ms (attempt $attempt/$maxAttempts)")
                    Thread.sleep(delayMs)
                    delayMs *= 2
                    continue
                }
                throw ex
            }
        }
    }
}
