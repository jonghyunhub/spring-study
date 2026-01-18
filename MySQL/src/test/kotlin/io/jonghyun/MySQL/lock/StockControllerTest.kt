package io.jonghyun.MySQL.lock

import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class StockControllerTest(
    private val dataSource: DataSource
) : IntegrationTest() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StockControllerTest::class.java)
    }

    @Test
    fun test(){
        // given

        // when

        // then

    }

}