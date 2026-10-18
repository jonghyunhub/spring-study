package io.jonghyun.circitbreaker.product

import com.github.benmanes.caffeine.cache.Cache
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.SlotHash
import io.lettuce.core.cluster.models.partitions.RedisClusterNode
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.protocol.CommandType
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Redis Cluster 에서 multiGet 이 MGET 몇 개로 나뉘는지, L1 캐시를 두면 어떻게 달라지는지 확인한다.
 *
 * Lettuce CommandListener 로 앱이 실제로 보낸 MGET 을 하나씩 기록해 출력하고,
 * Redis 서버 통계(INFO commandstats 의 cmdstat_mget)와 교차 검증한다.
 *
 * 실행 조건: docker/docker-compose-cluster.yml 기동 (DB 는 test 프로필의 H2 사용)
 *   ./gradlew :core-api:developTest --tests '*LayeredCacheMgetTest' -i
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.data.redis.cluster.nodes=localhost:7001,localhost:7002,localhost:7003,localhost:7004,localhost:7005,localhost:7006",
    ],
)
@ActiveProfiles("test")
class LayeredCacheMgetTest(
    @Autowired private val productCircuitBreakerService: ProductCircuitBreakerService,
    @Autowired private val redisTemplate: RedisTemplate<String, Product>,
    @Autowired private val productLocalCache: Cache<Long, Product>,
    @Autowired private val connectionFactory: LettuceConnectionFactory,
) {

    private val clusterClient get() = connectionFactory.nativeClient as RedisClusterClient
    private val mgetRecorder = MgetRecorder()

    @BeforeEach
    fun setUp() {
        (1L..100L).forEach { id ->
            redisTemplate.opsForValue().set("product:$id", Product(id, "product-$id", "ON_SALE"))
        }
        productLocalCache.invalidateAll()

        // CommandListener 는 연결이 만들어질 때 writer 에 감싸지므로, 등록 후 연결을 다시 만든다.
        clusterClient.addListener(mgetRecorder)
        connectionFactory.resetConnection()
    }

    @AfterEach
    fun tearDown() {
        clusterClient.removeListener(mgetRecorder)
        connectionFactory.resetConnection()
    }

    @Test
    @DisplayName("1. multiGet 키 50개는 slot 별 MGET 으로 나뉘어 전송된다")
    fun multiGetIsSplitBySlot() {
        val ids = (1L..50L).toList()

        val mgets = record("1) findFromCache 50개") { productCircuitBreakerService.findFromCache(ids) }

        assertThat(mgets).hasSize(distinctSlots(ids))
    }

    @Test
    @DisplayName("2. L1 을 두면 L1 miss 키에 대해서만 MGET 이 나간다")
    fun layeredCacheSendsMgetOnlyForL1Miss() {
        val first = (1L..50L).toList()
        val half = (26L..75L).toList() // 26~50 은 L1 hit, 51~75 는 L1 miss

        val cold = record("2) layered 처음 50개") { productCircuitBreakerService.findFromLayeredCache(first) }
        val warm = record("3) layered 같은 50개") { productCircuitBreakerService.findFromLayeredCache(first) }
        val partial = record("4) layered 절반 겹침") { productCircuitBreakerService.findFromLayeredCache(half) }

        assertThat(cold).hasSize(distinctSlots(first))
        assertThat(warm).isEmpty()
        assertThat(partial).hasSize(distinctSlots((51L..75L).toList()))
    }

    /** block 실행 동안 Lettuce 가 보낸 MGET 을 출력하고 반환한다. 서버 통계와 개수가 같은지도 확인한다. */
    private fun record(title: String, block: () -> Unit): List<String> {
        resetServerMgetCalls()
        mgetRecorder.clear()

        block()

        val mgets = mgetRecorder.snapshot()
        val serverMget = serverMgetCalls()
        println("\n[$title] Lettuce MGET ${mgets.size}개 / Redis 서버 MGET ${serverMget}개")
        mgets.forEach { println("  MGET $it") }

        assertThat(mgets.size.toLong()).`as`("Lettuce 기록과 서버 통계가 일치해야 한다").isEqualTo(serverMget)
        return mgets
    }

    private fun distinctSlots(ids: List<Long>): Int =
        ids.map { SlotHash.getSlot("product:$it") }.distinct().size

    // --- Redis 서버 통계 (master 별 INFO commandstats) ---

    private fun masters(): List<RedisClusterNode> =
        clusterClient.partitions.filter { it.`is`(RedisClusterNode.NodeFlag.UPSTREAM) }

    private fun resetServerMgetCalls() =
        masters().forEach { node -> connectionFactory.clusterConnection.serverCommands().resetConfigStats(toSpringNode(node)) }

    private fun serverMgetCalls(): Long =
        masters().sumOf { node ->
            val stats = connectionFactory.clusterConnection.serverCommands().info(toSpringNode(node), "commandstats")
            stats?.getProperty("cmdstat_mget")?.let { MGET_CALLS.find(it)?.groupValues?.get(1)?.toLong() } ?: 0L
        }

    private fun toSpringNode(node: RedisClusterNode) =
        org.springframework.data.redis.connection.RedisClusterNode(node.uri.host, node.uri.port)

    /**
     * 클러스터 노드는 자신을 Docker 네트워크 IP(192.168.x.x)로 알리는데, 호스트에서는 이 IP 로 접속할 수 없다.
     * 테스트에서만 노드 주소를 127.0.0.1 (포트 유지)로 바꿔 접속한다.
     */
    @TestConfiguration
    class DockerClusterAddressConfig {
        @Bean
        fun mapClusterNodesToLocalhost() = ClientResourcesBuilderCustomizer { builder ->
            builder.socketAddressResolver(
                MappingSocketAddressResolver.create(DnsResolvers.JVM_DEFAULT) { hostAndPort: HostAndPort ->
                    HostAndPort.of("127.0.0.1", hostAndPort.port)
                },
            )
        }
    }

    /** 앱의 Lettuce 클라이언트가 보낸 MGET 의 인자(키 목록)를 기록한다. */
    private class MgetRecorder : CommandListener {
        private val mgets = CopyOnWriteArrayList<String>()

        override fun commandStarted(event: CommandStartedEvent) {
            if (event.command.type == CommandType.MGET) {
                mgets.add(event.command.args.toCommandString())
            }
        }

        fun clear() = mgets.clear()

        fun snapshot(): List<String> = mgets.toList()
    }

    companion object {
        private val MGET_CALLS = Regex("calls=(\\d+)")
    }
}
