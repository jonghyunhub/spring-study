#!/usr/bin/env bash
# Redis 내부 경로 관찰 커맨드 모음
# k6 벤치마크 실행 후 실행: bash benchmark/redis-observe.sh

set -e

echo "=== String encoding ==="
redis-cli OBJECT ENCODING goods:string:1

echo ""
echo "=== Hash encoding ==="
redis-cli OBJECT ENCODING goods:hash:1

echo ""
echo "=== Memory usage: String key ==="
redis-cli MEMORY USAGE goods:string:1

echo ""
echo "=== Memory usage: Hash key ==="
redis-cli MEMORY USAGE goods:hash:1

echo ""
echo "=== commandstats (GET / HGETALL) ==="
redis-cli INFO commandstats | grep -E "cmdstat_get:|cmdstat_hgetall:"

echo ""
echo "=== SLOWLOG (최근 10개) ==="
redis-cli SLOWLOG GET 10

echo ""
echo "=== keyspace info ==="
redis-cli INFO keyspace
