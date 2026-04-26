package io.jonghyun.Elasticsearch.article

import io.jonghyun.Elasticsearch.article.dto.ArticleCreateRequest
import io.jonghyun.Elasticsearch.article.dto.ArticleSearchResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
    private val articleSearchService: ArticleSearchService,
) {
    @Transactional
    fun create(request: ArticleCreateRequest): ArticleSearchResponse {
        val article = articleRepository.save(
            Article(title = request.title, content = request.content, author = request.author)
        )
        articleSearchService.index(article)
        return ArticleSearchResponse.from(article)
    }

    // MySQL LIKE 검색: %keyword% 풀스캔 방식
    @Transactional(readOnly = true)
    fun searchByMySQL(keyword: String, size: Int): List<ArticleSearchResponse> {
        return articleRepository
            .findByTitleContainingOrContentContaining(keyword, keyword, PageRequest.of(0, size))
            .map { ArticleSearchResponse.from(it) }
    }

    // 비교용 대량 데이터 생성
    @Transactional
    fun createBulk(count: Int) {
        val articles = (1..count).map { i ->
            Article(
                title = "${TOPICS[i % TOPICS.size]} ${i}번 게시글",
                content = "<h1>${TOPICS[i % TOPICS.size]}</h1><p>${CONTENTS[i % CONTENTS.size]} (번호: $i)</p>",
                author = "작성자${i % 10 + 1}",
            )
        }
        val saved = articleRepository.saveAll(articles)
        articleSearchService.indexAll(saved)
    }

    companion object {
        private val TOPICS = listOf(
            "스프링 부트 입문", "자바 OOP 원칙", "코틀린 활용법", "데이터베이스 설계",
            "마이크로서비스 아키텍처", "도커 컨테이너 활용", "쿠버네티스 배포", "엘라스틱서치 학습",
            "레디스 캐싱 전략", "MySQL 인덱스 최적화", "REST API 설계", "클린 코드 작성법",
            "디자인 패턴 적용", "알고리즘 문제 풀이", "자료구조 이해", "테스트 주도 개발",
            "CI/CD 파이프라인 구축", "모니터링 시스템 구성", "보안 취약점 분석", "성능 튜닝 방법",
        )
        private val CONTENTS = listOf(
            "이 글에서는 기초부터 차근차근 설명합니다.",
            "실무에서 자주 사용되는 패턴을 소개합니다.",
            "예제 코드와 함께 자세히 알아보겠습니다.",
            "최신 트렌드를 반영한 내용으로 구성되어 있습니다.",
            "초보자도 쉽게 이해할 수 있도록 작성하였습니다.",
            "고급 개발자를 위한 심화 내용을 다룹니다.",
            "오픈소스 프로젝트를 분석하며 학습합니다.",
            "면접 준비에 도움이 되는 핵심 개념을 정리했습니다.",
        )
    }
}
