package io.jonghyun.Elasticsearch.article

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface ArticleSearchRepository : ElasticsearchRepository<ArticleDocument, String>
