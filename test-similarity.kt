import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.sql.DriverManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun main() {
    val db = DriverManager.getConnection("jdbc:sqlite:./linli_news.db")
    val mapper = jacksonObjectMapper()

    // Get article 62 embedding
    val articleStmt = db.prepareStatement("SELECT embedding FROM article_embedding WHERE article_id = 62")
    val articleRs = articleStmt.executeQuery()
    if (!articleRs.next()) {
        println("Article 62 has no embedding")
        return
    }
    val articleEmbedding = mapper.readValue(articleRs.getString("embedding"), DoubleArray::class.java)

    // Get all tags and calculate similarity
    val tagStmt = db.prepareStatement("SELECT id, name, embedding FROM article_tag_definition ORDER BY id")
    val tagRs = tagStmt.executeQuery()

    val similarities = mutableListOf<Triple<Long, String, Double>>()

    while (tagRs.next()) {
        val tagId = tagRs.getLong("id")
        val tagName = tagRs.getString("name")
        val tagEmbedding = mapper.readValue(tagRs.getString("embedding"), DoubleArray::class.java)

        val similarity = cosineSimilarity(articleEmbedding, tagEmbedding)
        similarities.add(Triple(tagId, tagName, similarity))
    }

    // Sort by similarity desc
    similarities.sortByDescending { it.third }

    println("Top 10 most similar tags for article 62:")
    println("%-5s %-40s %s".format("ID", "Tag Name", "Similarity"))
    println("-".repeat(60))
    similarities.take(10).forEach { (id, name, score) ->
        println("%-5d %-40s %.4f".format(id, name, score))
    }

    db.close()
}

fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
    val length = min(a.size, b.size)
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in 0 until length) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denominator = sqrt(max(normA, 1e-12)) * sqrt(max(normB, 1e-12))
    return if (denominator == 0.0) 0.0 else dot / denominator
}

main()
