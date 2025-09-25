package com.linli.news.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FullTextArticleResponse(
    @JsonProperty("title") val title: String?,
    @JsonProperty("excerpt") val excerpt: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("author") val author: String?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("effective_url") val effectiveUrl: String?,
    @JsonProperty("domain") val domain: String?,
    @JsonProperty("word_count") val wordCount: Int?,
    @JsonProperty("content") val content: String?
)
