package com.linli.news.db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * 將 legacy article_ai_content 與 explanation JSON 遷移至新結構。
 */
class V15__MigrateAiContentToViews : BaseJavaMigration() {
    private val mapper = ObjectMapper()

    override fun migrate(context: Context) {
        val connection = context.connection
        connection.prepareStatement(
            """
            SELECT c.id,
                   c.article_ai_id,
                   c.difficulty,
                   c.optimized_html,
                   c.optimized_zh_html,
                   c.cleaned_text,
                   c.cleaned_text_zh,
                   c.explanation,
                   c.created_at,
                   c.updated_at,
                   a.title,
                   a.title_zh,
                   a.summary_en,
                   a.summary_zh
            FROM article_ai_content c
            JOIN article_ai a ON a.id = c.article_ai_id
            """.trimIndent()
        ).use { stmt ->
            stmt.executeQuery().use { rows ->
                while (rows.next()) {
                    val viewId = insertView(connection, rows)
                    val explanationJson = rows.getString("explanation")
                    if (!explanationJson.isNullOrBlank()) {
                        runCatching { mapper.readTree(explanationJson) }
                            .onSuccess { node ->
                                migrateExplanation(
                                    connection,
                                    viewId,
                                    node,
                                    rows.getString("created_at"),
                                    rows.getString("updated_at")
                                )
                            }
                    }
                }
            }
        }

        connection.createStatement().use { stmt ->
            stmt.executeUpdate("ALTER TABLE article_ai_content RENAME TO article_ai_content_legacy")
        }
    }

    private fun insertView(connection: Connection, rows: ResultSet): Long {
        val insertSql = """
            INSERT INTO article_ai_view (
                article_ai_id,
                difficulty,
                from_lang,
                to_lang,
                title_from,
                title_to,
                summary_from,
                summary_to,
                optimized_html_from,
                optimized_html_to,
                cleaned_text_from,
                cleaned_text_to,
                legacy_explanation_json,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS).use { ps ->
            var idx = 1
            ps.setLong(idx++, rows.getLong("article_ai_id"))
            ps.setString(idx++, rows.getString("difficulty"))
            ps.setString(idx++, "en")
            ps.setString(idx++, "zh-TW")
            ps.setNullableString(idx++, rows.getString("title"))
            ps.setNullableString(idx++, rows.getString("title_zh"))
            ps.setNullableString(idx++, rows.getString("summary_en"))
            ps.setNullableString(idx++, rows.getString("summary_zh"))
            ps.setNullableString(idx++, rows.getString("optimized_html"))
            ps.setNullableString(idx++, rows.getString("optimized_zh_html"))
            ps.setNullableString(idx++, rows.getString("cleaned_text"))
            ps.setNullableString(idx++, rows.getString("cleaned_text_zh"))
            ps.setNullableString(idx++, rows.getString("explanation"))
            ps.setNullableString(idx++, rows.getString("created_at"))
            ps.setNullableString(idx, rows.getString("updated_at"))
            ps.executeUpdate()
            ps.generatedKeys.use { key ->
                if (key.next()) {
                    return key.getLong(1)
                }
            }
        }
        throw IllegalStateException("Failed to insert article_ai_view row")
    }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value != null) {
            setString(index, value)
        } else {
            setNull(index, Types.VARCHAR)
        }
    }

    private fun migrateExplanation(
        connection: Connection,
        viewId: Long,
        node: JsonNode,
        createdAt: String?,
        updatedAt: String?
    ) {
        insertVocabulary(connection, viewId, node.path("vocabulary"), createdAt, updatedAt)
        insertGrammar(connection, viewId, node.path("grammar"), createdAt, updatedAt)
        insertSentencePatterns(connection, viewId, node.path("sentence_patterns"), createdAt, updatedAt)
        insertPhrases(connection, viewId, node.path("phrases_idioms"), createdAt, updatedAt)
        insertMcq(connection, viewId, node.path("comprehension_mcq"), createdAt, updatedAt)
    }

    private fun insertVocabulary(
        connection: Connection,
        viewId: Long,
        node: JsonNode?,
        createdAt: String?,
        updatedAt: String?
    ) {
        if (node == null || !node.isArray || node.isEmpty) return
        val sql = """
            INSERT INTO ai_vocabulary_item (
                article_ai_view_id,
                sort_order,
                pos,
                word_from,
                word_to,
                definition_from,
                definition_to,
                example_from,
                example_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            var index = 0
            node.forEach { item ->
                ps.setLong(1, viewId)
                ps.setInt(2, index++)
                ps.setString(3, item.path("pos").asText(null))
                ps.setString(4, item.path("word_en").asText(null))
                ps.setString(5, item.path("word_zh").asText(null))
                ps.setString(6, item.path("definition_en").asText(null))
                ps.setString(7, item.path("definition_zh").asText(null))
                ps.setString(8, item.path("example_en").asText(null))
                ps.setString(9, item.path("example_zh").asText(null))
                ps.setString(10, createdAt)
                ps.setString(11, updatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun insertGrammar(connection: Connection, viewId: Long, node: JsonNode?, createdAt: String?, updatedAt: String?) {
        if (node == null || !node.isArray || node.isEmpty) return
        val sql = """
            INSERT INTO ai_grammar_item (
                article_ai_view_id,
                sort_order,
                rule_from,
                rule_to,
                explanation_from,
                explanation_to,
                example_from,
                example_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            var index = 0
            node.forEach { item ->
                ps.setLong(1, viewId)
                ps.setInt(2, index++)
                ps.setString(3, item.path("rule_en").asText(null))
                ps.setString(4, null)
                ps.setString(5, item.path("explanation_en").asText(null))
                ps.setString(6, item.path("explanation_zh").asText(null))
                ps.setString(7, item.path("example_en").asText(null))
                ps.setString(8, item.path("example_zh").asText(null))
                ps.setString(9, createdAt)
                ps.setString(10, updatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun insertSentencePatterns(
        connection: Connection,
        viewId: Long,
        node: JsonNode?,
        createdAt: String?,
        updatedAt: String?
    ) {
        if (node == null || !node.isArray || node.isEmpty) return
        val sql = """
            INSERT INTO ai_sentence_pattern_item (
                article_ai_view_id,
                sort_order,
                pattern_from,
                pattern_to,
                explanation_from,
                explanation_to,
                example_from,
                example_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            var index = 0
            node.forEach { item ->
                ps.setLong(1, viewId)
                ps.setInt(2, index++)
                ps.setString(3, item.path("pattern_en").asText(null))
                ps.setString(4, null)
                ps.setString(5, item.path("explanation_en").asText(null))
                ps.setString(6, item.path("explanation_zh").asText(null))
                ps.setString(7, item.path("example_en").asText(null))
                ps.setString(8, item.path("example_zh").asText(null))
                ps.setString(9, createdAt)
                ps.setString(10, updatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun insertPhrases(connection: Connection, viewId: Long, node: JsonNode?, createdAt: String?, updatedAt: String?) {
        if (node == null || !node.isArray || node.isEmpty) return
        val sql = """
            INSERT INTO ai_phrase_idiom_item (
                article_ai_view_id,
                sort_order,
                phrase_from,
                phrase_to,
                explanation_from,
                explanation_to,
                example_from,
                example_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            var index = 0
            node.forEach { item ->
                ps.setLong(1, viewId)
                ps.setInt(2, index++)
                ps.setString(3, item.path("phrase_en").asText(null))
                ps.setString(4, null)
                ps.setString(5, item.path("explanation_en").asText(null))
                ps.setString(6, item.path("explanation_zh").asText(null))
                ps.setString(7, item.path("example_en").asText(null))
                ps.setString(8, item.path("example_zh").asText(null))
                ps.setString(9, createdAt)
                ps.setString(10, updatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun insertMcq(connection: Connection, viewId: Long, node: JsonNode?, createdAt: String?, updatedAt: String?) {
        if (node == null || !node.isArray || node.isEmpty) return
        val sql = """
            INSERT INTO ai_comprehension_mcq (
                article_ai_view_id,
                sort_order,
                question_from,
                question_to,
                answer,
                explanation_from,
                explanation_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val optionSql = """
            INSERT INTO ai_comprehension_option (
                mcq_id,
                label,
                text_from,
                text_to,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS).use { mcqStmt ->
            connection.prepareStatement(optionSql).use { optionStmt ->
                var index = 0
                node.forEach { item ->
                    mcqStmt.setLong(1, viewId)
                    mcqStmt.setInt(2, index++)
                    mcqStmt.setString(3, item.path("question_en").asText(null))
                    mcqStmt.setString(4, item.path("question_zh").asText(null))
                    mcqStmt.setString(5, item.path("answer").asText(null))
                    mcqStmt.setString(6, null)
                    mcqStmt.setString(7, item.path("explanation_zh").asText(null))
                    mcqStmt.setString(8, createdAt)
                    mcqStmt.setString(9, updatedAt)
                    mcqStmt.executeUpdate()
                    val key = mcqStmt.generatedKeys
                    key.next()
                    val mcqId = key.getLong(1)
                    key.close()

                    val optionsNode = item.path("options")
                    if (optionsNode != null && optionsNode.isObject) {
                        listOf("A", "B", "C", "D").forEach { label ->
                            optionStmt.setLong(1, mcqId)
                            optionStmt.setString(2, label)
                            optionStmt.setString(3, optionsNode.path(label).asText(null))
                            optionStmt.setString(4, null)
                            optionStmt.setString(5, createdAt)
                            optionStmt.setString(6, updatedAt)
                            optionStmt.addBatch()
                        }
                        optionStmt.executeBatch()
                    }
                }
            }
        }
    }
}
