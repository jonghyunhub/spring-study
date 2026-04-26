package io.jonghyun.Elasticsearch.article

import io.jonghyun.Elasticsearch.article.dto.ArticleCreateRequest
import io.jonghyun.Elasticsearch.article.dto.ArticleSearchResponse
import io.jonghyun.Elasticsearch.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/articles")
class ArticleController(
    private val articleService: ArticleService,
    private val articleSearchService: ArticleSearchService,
) {
    @PostMapping
    fun create(@RequestBody request: ArticleCreateRequest): ApiResponse<ArticleSearchResponse> {
        return ApiResponse.success(articleService.create(request))
    }

    // MySQL LIKE %keyword% 검색
    @GetMapping("/search/mysql")
    fun searchMySQL(
        @RequestParam q: String,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<ArticleSearchResponse>> {
        return ApiResponse.success(articleService.searchByMySQL(q, size))
    }

    // Elasticsearch multi_match 검색 (nori 형태소 분석 + html_strip)
    @GetMapping("/search/es")
    fun searchES(
        @RequestParam q: String,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<ArticleSearchResponse>> {
        return ApiResponse.success(articleSearchService.search(q, size))
    }

    // 비교용 대량 데이터 생성
    @PostMapping("/bulk")
    fun createBulk(@RequestParam(defaultValue = "10000") count: Int): ApiResponse<String> {
        articleService.createBulk(count)
        return ApiResponse.success("${count}개 게시글 생성 완료")
    }
}
