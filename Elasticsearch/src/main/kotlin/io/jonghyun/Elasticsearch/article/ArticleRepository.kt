package io.jonghyun.Elasticsearch.article

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ArticleRepository : JpaRepository<Article, Long> {
    // MySQL LIKE 검색: title 또는 content에 keyword가 포함된 게시글 조회
    fun findByTitleContainingOrContentContaining(
        title: String,
        content: String,
        pageable: Pageable,
    ): List<Article>
}
