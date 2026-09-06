#!/usr/bin/env bash
# MySQL 내부 경로 관찰 커맨드 모음
# k6 벤치마크 실행 후 실행: bash benchmark/mysql-observe.sh
# 로컬에 mysql CLI를 설치하지 않고, mysql 컨테이너에 내장된 클라이언트로 접속합니다.
# 컨테이너/유저/DB 이름은 docker-compose.yml 기준 기본값. 필요하면 환경변수로 덮어쓰기.
# 비밀번호는 매 명령마다 물어봅니다 (-p 사용, 별도 인증 파일 설정 없음).

set -e

MYSQL_CONTAINER="${MYSQL_CONTAINER:-jonghyun-redis-mysql}"
MYSQL_USER="${MYSQL_USER:-jonghyun-mysql}"
MYSQL_DB="${MYSQL_DB:-redisdb}"

mysql_exec() {
  docker exec -it "$MYSQL_CONTAINER" mysql -u "$MYSQL_USER" -p "$MYSQL_DB" -e "$1"
}

echo "=== EXPLAIN: PK lookup ==="
mysql_exec "EXPLAIN SELECT id, name, price, stock, category FROM goods WHERE id = 1;"

echo ""
echo "=== EXPLAIN ANALYZE: PK lookup ==="
mysql_exec "EXPLAIN ANALYZE SELECT id, name, price, stock, category FROM goods WHERE id = 1;"

echo ""
echo "=== Handler stats (읽기 방식별 누적 횟수) ==="
mysql_exec "SHOW STATUS LIKE 'Handler_read%';"

echo ""
echo "=== goods 테이블 상태 (row 수 / 데이터 크기 / 인덱스 크기) ==="
mysql_exec "SHOW TABLE STATUS LIKE 'goods';"

echo ""
echo "=== InnoDB buffer pool 요약 ==="
mysql_exec "SHOW STATUS LIKE 'Innodb_buffer_pool_read%';"
