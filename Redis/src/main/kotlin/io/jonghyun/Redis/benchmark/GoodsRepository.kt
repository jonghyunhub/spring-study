package io.jonghyun.Redis.benchmark

import org.springframework.data.jpa.repository.JpaRepository

interface GoodsRepository : JpaRepository<Goods, Long>
