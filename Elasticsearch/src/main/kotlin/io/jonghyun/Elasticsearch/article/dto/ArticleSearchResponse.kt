package io.jonghyun.Elasticsearch.article.dto

import io.jonghyun.Elasticsearch.article.Article
import io.jonghyun.Elasticsearch.article.ArticleDocument
import java.time.LocalDateTime

data class ArticleSearchResponse(
    val id: Long,
    val title: String,
    val content: String,
    val author: String,
    val createdAt: LocalDateTime,
    val score: Float? = null, // ES 관련도 점수 (MySQL 검색 시 null)
) {
    companion object {
        fun from(article: Article) = ArticleSearchResponse(
            id = article.id,
            title = article.title,
            content = article.content,
            author = article.author,
            createdAt = article.createdAt,
        )

        fun from(document: ArticleDocument, score: Float?) = ArticleSearchResponse(
            id = document.id.toLong(),
            title = document.title,
            content = document.content,
            author = document.author,
            createdAt = document.createdAt,
            score = score,
        )
    }
}
