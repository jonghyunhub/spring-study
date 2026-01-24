package io.jonghyun.Redis.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = ["io.jonghyun.Redis"])
@EnableJpaRepositories(basePackages = ["io.jonghyun.Redis"])
internal class CoreJpaConfig
