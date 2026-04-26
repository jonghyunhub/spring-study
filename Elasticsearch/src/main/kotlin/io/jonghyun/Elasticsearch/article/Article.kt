package io.jonghyun.Elasticsearch.article

import io.jonghyun.Elasticsearch.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "articles")
class Article(
    val title: String,
    @Column(columnDefinition = "TEXT")
    val content: String,
    val author: String,
) : BaseEntity()