package com.linli.news.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

/**
 * 自定義反序列化器，確保只處理大寫的 A, B, C, D 選項
 * 忽略小寫重複項
 */
class McqOptionsDeserializer : JsonDeserializer<McqOptions>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): McqOptions {
        val node: JsonNode = parser.codec.readTree(parser)

        // 只提取大寫的選項，忽略小寫重複項
        val optionA = node.get("A")?.asText()
        val optionB = node.get("B")?.asText()
        val optionC = node.get("C")?.asText()
        val optionD = node.get("D")?.asText()

        return McqOptions(
            A = optionA,
            B = optionB,
            C = optionC,
            D = optionD
        )
    }
}