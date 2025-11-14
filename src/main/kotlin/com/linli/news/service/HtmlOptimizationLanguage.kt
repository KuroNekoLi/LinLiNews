package com.linli.news.service

enum class HtmlOptimizationLanguage(val instruction: String) {
    ENGLISH("保留英文原文內容"),
    TRADITIONAL_CHINESE("全文翻譯為繁體中文，保留原段落結構與語意")
}