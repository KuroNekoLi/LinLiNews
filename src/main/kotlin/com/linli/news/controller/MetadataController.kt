package com.linli.news.controller

import com.linli.news.model.ArticleTagDefinitionDto
import com.linli.news.model.FeaturedCollectionDefinitionDto
import com.linli.news.service.ArticleTagService
import com.linli.news.service.FeaturedCollectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/metadata")
@Tag(name = "Metadata", description = "標籤與專題等元數據管理 API")
class MetadataController(
    private val articleTagService: ArticleTagService,
    private val featuredCollectionService: FeaturedCollectionService
) {

    @GetMapping("/tags")
    @Operation(summary = "取得所有 Tags", description = "取得系統中所有可用的標籤清單")
    fun getAllTags(): List<ArticleTagDefinitionDto> {
        return articleTagService.getAllTags()
    }

    @GetMapping("/collections")
    @Operation(summary = "取得所有精選專題", description = "取得系統中所有可用的精選專題清單")
    fun getAllCollections(): List<FeaturedCollectionDefinitionDto> {
        return featuredCollectionService.getAllCollections()
    }
}
