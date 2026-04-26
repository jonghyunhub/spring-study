package io.jonghyun.Elasticsearch.article

import jakarta.persistence.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting
import java.time.LocalDateTime

@Document(indexName = "articles")
@Setting(settingPath = "/elasticsearch/articles-settings.json")
data class ArticleDocument(
    @Id val id: String,
    // nori 형태소 분석기 + html_strip 캐릭터 필터 적용
    @Field(type = FieldType.Text, analyzer = "nori_html")
    val title: String,
    @Field(type = FieldType.Text, analyzer = "nori_html")
    val content: String,
    @Field(type = FieldType.Keyword)
    val author: String,
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
    val createdAt: LocalDateTime,
)