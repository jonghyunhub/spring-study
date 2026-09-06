# Redis vs MySQL benchmark

This benchmark compares the existing Spring endpoints:

- `mysql`: JPA `findById`
- `mysql-jdbc`: `JdbcTemplate` single-row lookup
- `redis-string`: Redis string value with JSON deserialization
- `redis-hash`: Redis hash lookup

## How it works

The Spring API exposes one seed endpoint and four read endpoints.

```text
POST /benchmark/seed?count={COUNT}
GET  /benchmark/mysql/{id}
GET  /benchmark/mysql-jdbc/{id}
GET  /benchmark/redis-string/{id}
GET  /benchmark/redis-hash/{id}
```

The seed endpoint resets the benchmark data and inserts the same logical goods
data into MySQL and Redis:

- MySQL `goods` rows
- Redis string keys: `goods:string:{id}`
- Redis hash keys: `goods:hash:{id}`

The k6 scripts only issue read requests. Each request picks one id and calls
one target endpoint.

```text
k6/mysql.js        -> GET /benchmark/mysql/{id}
k6/mysql-jdbc.js   -> GET /benchmark/mysql-jdbc/{id}
k6/redis-string.js -> GET /benchmark/redis-string/{id}
k6/redis-hash.js   -> GET /benchmark/redis-hash/{id}
```

All k6 scripts share the same id-picking logic in `k6/lib/benchmark.js`.
The request URL is tagged as `/benchmark/{target}/:id` so k6 groups dynamic ids
into one metric series per target.

`DISTRIBUTION=uniform` means every id has the same chance of being selected.

```text
COUNT=100000
id = random 1..100000
```

`DISTRIBUTION=hot` means most traffic goes to a small hot range.

```text
COUNT=1000000
HOT_PERCENT=0.01
HOT_RATIO=0.8

hot range  = 1..10000
cold range = 10001..1000000

80% of requests -> random id from hot range
20% of requests -> random id from cold range
```

The scenario runner, `run-scenarios.sh`, loops in this order:

```text
for each COUNT:
  seed COUNT records unless SKIP_SEED=true
  for each DISTRIBUTION:
    for each VUS:
      for each TARGET:
        run k6 and export one JSON summary
```

This keeps MySQL and Redis loaded with the same number of records for every
target in the same data-size group.

## 1. Start infrastructure

Start MySQL, Redis, Prometheus, Grafana, and exporters first.

```bash
cd /Users/jonghchoo/Desktop/study/spring-study/Redis
docker compose -f docker/docker-compose.yml up -d
```

## 2. Start Spring API

The benchmark runner does not start Spring. Keep the Spring API running in a
separate terminal.

```bash
cd /Users/jonghchoo/Desktop/study/spring-study/Redis
./gradlew bootRun
```

The benchmark assumes the API is available at:

```text
http://localhost:8080
```

## 3. Smoke test

Before running k6, verify that seed, MySQL lookup, and Redis lookup work.

```bash
curl -X POST "http://localhost:8080/benchmark/seed?count=10"
curl "http://localhost:8080/benchmark/mysql/1"
curl "http://localhost:8080/benchmark/redis-string/1"
curl "http://localhost:8080/benchmark/redis-hash/1"
```

## 4. Run a small first pass

Run this before the full matrix. It seeds 100,000 records and compares MySQL
JPA with Redis string under both uniform and hot-key traffic.

```bash
cd /Users/jonghchoo/Desktop/study/spring-study/Redis/benchmark

COUNT_LIST="100000" \
VUS_LIST="50" \
DISTRIBUTION_LIST="uniform hot" \
TARGET_LIST="mysql redis-string" \
./run-scenarios.sh
```

## 5. Run the full matrix

```bash
cd /Users/jonghchoo/Desktop/study/spring-study/Redis/benchmark
./run-scenarios.sh
```

The runner calls the seed API before each data-size group:

```bash
POST /benchmark/seed?count={COUNT}
```

So `COUNT_LIST="100000 1000000"` means:

1. seed 100,000 rows and Redis keys
2. run every distribution/VU/target scenario for 100,000
3. seed 1,000,000 rows and Redis keys
4. run every distribution/VU/target scenario for 1,000,000

Default matrix:

- `COUNT_LIST="100000 1000000"`
- `DISTRIBUTION_LIST="uniform hot"`
- `VUS_LIST="50 100 200"`
- `TARGET_LIST="mysql mysql-jdbc redis-string redis-hash"`
- `DURATION="60s"`
- `HOT_RATIO="0.8"`
- `HOT_PERCENT="0.01"`
- `SKIP_SEED="false"`
- `HTML_REPORTS="true"`
- `WEB_DASHBOARD_PERIOD="5s"`

This runs `2 x 2 x 3 x 4 = 48` k6 tests.

## 6. Tune scenarios

Run only the 1,000,000-record hot-key scenario:

```bash
COUNT_LIST="1000000" \
DISTRIBUTION_LIST="hot" \
VUS_LIST="100" \
TARGET_LIST="mysql mysql-jdbc redis-string redis-hash" \
./run-scenarios.sh
```

Change the hot-key shape:

```bash
HOT_RATIO="0.9" \
HOT_PERCENT="0.01" \
COUNT_LIST="1000000" \
DISTRIBUTION_LIST="hot" \
./run-scenarios.sh
```

This means the first 1% of ids receive 90% of requests.

Reuse already-seeded data:

```bash
SKIP_SEED=true COUNT_LIST="1000000" ./run-scenarios.sh
```

Disable HTML report generation:

```bash
HTML_REPORTS=false ./run-scenarios.sh
```

## 7. Single k6 run

Use this when you want to manually test one target.

```bash
cd /Users/jonghchoo/Desktop/study/spring-study/Redis/benchmark

k6 run \
  -e COUNT=100000 \
  -e DISTRIBUTION=uniform \
  -e VUS=50 \
  -e DURATION=60s \
  k6/mysql.js
```

Live web dashboard example:

```bash
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_OPEN=true \
k6 run \
  -e COUNT=100000 \
  -e DISTRIBUTION=uniform \
  -e VUS=50 \
  -e DURATION=60s \
  k6/mysql.js
```

By default, the live dashboard is available at:

```text
http://127.0.0.1:5665
```

Hot-key example with an exported HTML report:

```bash
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_PERIOD=5s \
K6_WEB_DASHBOARD_EXPORT=redis-string-hot.html \
k6 run \
  -e COUNT=1000000 \
  -e DISTRIBUTION=hot \
  -e HOT_RATIO=0.8 \
  -e HOT_PERCENT=0.01 \
  -e VUS=100 \
  -e DURATION=60s \
  k6/redis-string.js
```

## 8. Results

Each run exports a k6 JSON summary under:

```text
results/{timestamp}
```

When `HTML_REPORTS=true`, each run also exports a self-contained HTML report
in the same directory.

Examples:

```text
seed-100000.txt
100000-uniform-50vu-mysql.json
100000-uniform-50vu-mysql.html
100000-uniform-50vu-mysql-jdbc.json
100000-uniform-50vu-redis-string.json
100000-uniform-50vu-redis-hash.json
100000-hot-50vu-mysql.json
```

Check these k6 metrics first:

- `http_reqs`
- `http_req_duration` avg
- `http_req_duration` p95
- `http_req_duration` p99
- `http_req_failed`

Check these Grafana panels together:

- MySQL Query Rate
- MySQL InnoDB Buffer Pool Hit Ratio
- MySQL InnoDB Buffer Pool Reads
- Spring Hikari Pool State
- Spring Hikari Acquire / Timeout
- Redis Command Rate
- Redis Clients
- Redis Memory
