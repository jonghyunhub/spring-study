package io.jonghyun.Elasticsearch.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = ["io.jonghyun.Elasticsearch"])
@EnableJpaRepositories(basePackages = ["io.jonghyun.Elasticsearch"])
internal class CoreJpaConfig
