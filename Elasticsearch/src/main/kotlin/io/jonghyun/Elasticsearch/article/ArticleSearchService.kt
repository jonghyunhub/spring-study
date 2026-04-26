package io.jonghyun.Elasticsearch.article

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import io.jonghyun.Elasticsearch.article.dto.ArticleSearchResponse
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

@Service
class ArticleSearchService(
    private val articleSearchRepository: ArticleSearchRepository,
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    fun index(article: Article) {
        articleSearchRepository.save(article.toDocument())
    }

    fun indexAll(articles: List<Article>) {
        articleSearchRepository.saveAll(articles.map { it.toDocument() })
    }

    // ES multi_match 검색: nori 형태소 분석 + html_strip 적용, 관련도 점수(_score) 반환
    fun search(keyword: String, size: Int): List<ArticleSearchResponse> {
        val query = NativeQuery.builder()
            .withQuery(
                Query.of { q ->
                    q.multiMatch { mm ->
                        mm.query(keyword).fields("title", "content")
                    }
                }
            )
            .withMaxResults(size)
            .build()

        return elasticsearchOperations.search(query, ArticleDocument::class.java)
            .searchHits  // List<SearchHit<ArticleDocument>> 로 꺼낸 뒤 map
            .map { hit -> ArticleSearchResponse.from(hit.content, hit.score) }
    }

    private fun Article.toDocument() = ArticleDocument(
        id = id.toString(),
        title = title,
        content = content,
        author = author,
        createdAt = createdAt,
    )
}
