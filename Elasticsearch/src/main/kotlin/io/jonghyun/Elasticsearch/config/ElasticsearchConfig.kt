package io.jonghyun.Elasticsearch.config

import io.jonghyun.Elasticsearch.article.ArticleDocument
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.core.ElasticsearchOperations

@Configuration
class ElasticsearchConfig(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    // 애플리케이션 시작 시 nori_html 분석기 설정이 포함된 인덱스 생성
    @Bean
    fun initArticleIndex(): ApplicationRunner = ApplicationRunner {
        val indexOps = elasticsearchOperations.indexOps(ArticleDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
        }
    }
}
