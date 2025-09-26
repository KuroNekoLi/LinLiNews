package com.linli.news.service

/**
 * Thrown when OpenAI returns insufficient_quota (retrying won't help).
 */
class AiQuotaException(message: String) : RuntimeException(message)

/** For other OpenAI API failures (non-quota). */
class AiApiException(message: String) : RuntimeException(message)
