package io.jonghyun.Redis.cluster

import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductRepository
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor

/**
 * Redis Cluster 환경에서 Lettuce의 내부 동작을 관찰하는 테스트
 *
 * 테스트 실행 시 콘솔 로그에서 다음을 확인하세요:
 * 1. [Topology 자동 탐색] 클라이언트 생성 시 CLUSTER NODES 명령으로 토폴로지 캐싱
 * 2. [Hash Slot 계산] 키 저장 시 해당 키의 slot을 계산해 올바른 노드로 직접 라우팅
 * 3. [MOVED 리디렉션] MOVED 응답을 받으면 토폴로지를 갱신하고 재시도
 * 4. [Hash Tag] {tag}로 묶인 키들이 같은 노드로 라우팅됨
 */
@Tag("cluster")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cluster")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ClusterBehaviorTest(
    private val productRepository: ProductRepository
) {

    private lateinit var clusterClient: RedisClusterClient
    private lateinit var connection: StatefulRedisClusterConnection<String, String>

    @BeforeEach
    fun setUp() {
        // Lettuce 클러스터 클라이언트 직접 생성
        val nodes = listOf(
            RedisURI.create("redis://localhost:7001"),
            RedisURI.create("redis://localhost:7002"),
            RedisURI.create("redis://localhost:7003"),
            RedisURI.create("redis://localhost:7004"),
            RedisURI.create("redis://localhost:7005"),
            RedisURI.create("redis://localhost:7006"),
        )
        clusterClient = RedisClusterClient.create(nodes)
        connection = clusterClient.connect()
        println("\n========== Lettuce ClusterClient 생성 완료 ==========")
        println("초기 접속 시 CLUSTER NODES 명령으로 토폴로지 자동 로드됨")
        println("======================================================\n")
    }

    @AfterEach
    fun tearDown() {
        connection.close()
        clusterClient.shutdown()
    }

    @Test
    @DisplayName("1. Cluster Topology 자동 탐색 확인")
    fun clusterTopologyDiscovery() {
        // Lettuce가 생성 시 CLUSTER NODES로 토폴로지를 이미 로드한 상태
        val sync = connection.sync()

        val clusterInfo = sync.clusterInfo()
        println("========== Cluster Topology ==========")
        println("  Info: $clusterInfo")
        println("======================================")

        val rawNodes = sync.clusterNodes()
        println("\n========== Raw CLUSTER NODES ==========")
        println(rawNodes)
        println("========================================")

        assert(rawNodes.contains("master"))
        assert(rawNodes.contains("slave"))
    }

    @Test
    @DisplayName("2. 키별 Hash Slot 계산과 라우팅")
    fun hashSlotCalculationAndRouting() {
        val sync = connection.sync()

        val keys = listOf("user:1", "product:100", "order:999", "session:abc", "cart:xyz")

        println("========== Key -> Hash Slot Mapping ==========")
        keys.forEach { key ->
            // HashSlot 계산
            val slot = io.lettuce.core.cluster.SlotHash.getSlot(key)
            // 해당 키를 담당하는 노드 조회
            val partition = connection.partitions.getPartitionBySlot(slot)
            println("  $key  ->  slot: $slot  ->  node: ${partition?.uri?.host}:${partition?.uri?.port}")
        }
        println("================================================")

        // when: 실제 저장 (Lettuce가 slot 기반으로 올바른 노드에 직접 라우팅)
        keys.forEach { key ->
            sync.set(key, "value-of-$key")
        }

        // then: 정상 조회
        keys.forEach { key ->
            assert(sync.get(key) == "value-of-$key") { "$key not found" }
        }

        // 정리
        keys.forEach { sync.del(it) }
    }

    @Test
    @DisplayName("3. Hash Tag로 같은 노드에 묶기")
    fun hashTagGroupsKeysOnSameNode() {
        val sync = connection.sync()

        // Hash Tag가 다른 경우
        val slot1 = io.lettuce.core.cluster.SlotHash.getSlot("user:1:profile")
        val slot2 = io.lettuce.core.cluster.SlotHash.getSlot("product:1:detail")
        val node1 = connection.partitions.getPartitionBySlot(slot1)
        val node2 = connection.partitions.getPartitionBySlot(slot2)

        println("========== Hash Tag 비교 ==========")
        println("  user:1:profile       -> slot: $slot1  -> node: ${node1?.uri?.host}:${node1?.uri?.port}")
        println("  product:1:detail     -> slot: $slot2  -> node: ${node2?.uri?.host}:${node2?.uri?.port}")
        println("  같은 노드인가?         -> ${node1?.uri == node2?.uri}")

        // Hash Tag가 같은 경우 - {user} 태그로 묶음
        val slot3 = io.lettuce.core.cluster.SlotHash.getSlot("{user}:1:profile")
        val slot4 = io.lettuce.core.cluster.SlotHash.getSlot("{user}:1:orders")
        val slot5 = io.lettuce.core.cluster.SlotHash.getSlot("{user}:1:cart")
        val node3 = connection.partitions.getPartitionBySlot(slot3)
        val node4 = connection.partitions.getPartitionBySlot(slot4)
        val node5 = connection.partitions.getPartitionBySlot(slot5)

        println("  {user}:1:profile     -> slot: $slot3  -> node: ${node3?.uri?.host}:${node3?.uri?.port}")
        println("  {user}:1:orders      -> slot: $slot4  -> node: ${node4?.uri?.host}:${node4?.uri?.port}")
        println("  {user}:1:cart        -> slot: $slot5  -> node: ${node5?.uri?.host}:${node5?.uri?.port}")
        println("  같은 노드인가?         -> ${node3?.uri == node4?.uri && node4?.uri == node5?.uri}")
        println("====================================")

        // when: 같은 Hash Tag로 묶인 키들에 MGET 실행
        sync.set("{user}:1:profile", "profile-data")
        sync.set("{user}:1:orders", "orders-data")
        sync.set("{user}:1:cart", "cart-data")

        val results = sync.mget("{user}:1:profile", "{user}:1:orders", "{user}:1:cart")

        // then: 한 번에 조회 성공 (CROSSSLOT 에러 없음)
        println("========== Hash Tag MGET 결과 ==========")
        results.forEach { println("  ${it.key} = ${it.value}") }
        println("========================================")

        assert(results.size == 3)

        // 정리
        sync.del("{user}:1:profile", "{user}:1:orders", "{user}:1:cart")
    }

    @Test
    @DisplayName("4. @Cacheable이 Cluster에서 어떻게 동작하는지")
    fun cacheableBehaviorOnCluster() {
        // given: 상품 데이터 삽입
        val product = productRepository.save(Product("iPhone 15", 1_500_000))
        val sync = connection.sync()

        // when: @Cacheable이 사용하는 캐시 키 패턴
        val cacheKey = "products::${product.id}"
        val slot = io.lettuce.core.cluster.SlotHash.getSlot(cacheKey)
        val partition = connection.partitions.getPartitionBySlot(slot)

        println("========== @Cacheable Cluster 라우팅 ==========")
        println("  캐시 키: $cacheKey")
        println("  Hash Slot: $slot")
        println("  담당 노드: ${partition?.uri?.host}:${partition?.uri?.port}")
        println("================================================")

        // @Cacheable이 DB에서 조회 후 캐시에 저장 (직접 시뮬레이션)
        val dto = product.toDto()
        val json = """{"id":${dto.id},"name":"${dto.name}","price":${dto.price}}"""
        sync.set(cacheKey, json)

        // then: 캐시에서 조회
        val cached = sync.get(cacheKey)
        println("  캐시 저장 값: $cached")

        // 정리
        sync.del(cacheKey)
        productRepository.delete(product)
    }

    @Test
    @DisplayName("5. MOVED Redirect 상황 시뮬레이션")
    fun movedRedirectSimulation() {
        val sync = connection.sync()

        val key = "moved-test-key"
        val slot = io.lettuce.core.cluster.SlotHash.getSlot(key)
        val partition = connection.partitions.getPartitionBySlot(slot)

        println("========== MOVED Redirect 관찰 ==========")
        println("  키: $key")
        println("  Hash Slot: $slot")
        println("  Lettuce가 라우팅할 노드: ${partition?.uri?.host}:${partition?.uri?.port}")

        // 저장 시 Lettuce가 토폴로지를 기반으로 올바른 노드로 직접 라우팅
        // (이미 slot -> node 매핑을 알고 있어 MOVED를 받지 않고 바로 올바른 곳으로 감)
        sync.set(key, "test-value")

        // CLUSTER GETKEYSINSLOT으로 해당 slot의 키 확인
        val keysInSlot = sync.clusterGetKeysInSlot(slot, 10)
        println("  해당 slot에 있는 키들: $keysInSlot")
        println("===========================================")

        // Lettuce는 내부적으로 slot -> node 매핑을 캐싱하고 있어
        // 매번 MOVED를 받지 않고 직접 올바른 노드로 라우팅함

        // 정리
        sync.del(key)
    }
}
