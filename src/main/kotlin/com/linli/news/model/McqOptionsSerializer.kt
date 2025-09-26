package com.linli.news.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

/**
 * Custom serializer to ensure only uppercase option keys (A/B/C/D) are written.
 */
class McqOptionsSerializer : JsonSerializer<McqOptions>() {
    override fun serialize(value: McqOptions?, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        if (value != null) {
            value.A?.let { gen.writeStringField("A", it) }
            value.B?.let { gen.writeStringField("B", it) }
            value.C?.let { gen.writeStringField("C", it) }
            value.D?.let { gen.writeStringField("D", it) }
        }
        gen.writeEndObject()
    }
}
