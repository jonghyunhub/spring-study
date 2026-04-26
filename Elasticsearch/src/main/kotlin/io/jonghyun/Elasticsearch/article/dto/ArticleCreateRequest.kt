package io.jonghyun.Elasticsearch.article.dto

data class ArticleCreateRequest(
    val title: String,
    val content: String, // HTML 허용
    val author: String,
)
