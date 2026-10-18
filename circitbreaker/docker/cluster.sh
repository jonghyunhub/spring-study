#!/usr/bin/env bash
# layered cache 실험용 Redis Cluster 관측 스크립트
#   ./cluster.sh status    cluster 상태 / master 별 slot
#   ./cluster.sh reset     모든 master 의 commandstats 초기화 (실험 직전)
#   ./cluster.sh stats     master 별 MGET 호출 수(서버 기준) + key 수 (실험 직후)
#   ./cluster.sh monitor   모든 master MONITOR (요청 1건이 MGET 몇 개로 나뉘는지 관찰, Ctrl+C 종료)
set -euo pipefail

rcli() { docker exec cb-redis-node-1 redis-cli "$@"; }

masters() {
  rcli -p 7001 cluster nodes | awk '/master/ && !/fail/ {split($2, a, "@"); n = split(a[1], b, ":"); print b[n]}' | sort
}

case "${1:-}" in
  status)
    rcli -p 7001 cluster info | grep -E "cluster_state|cluster_slots_assigned|cluster_size"
    rcli -p 7001 cluster nodes | awk '/master/ {split($2, a, "@"); printf "%s: %s\n", a[1], $9}' | sort
    ;;
  reset)
    for p in $(masters); do rcli -h host.docker.internal -p "$p" config resetstat >/dev/null; done
    echo "commandstats reset: $(masters | tr '\n' ' ')"
    ;;
  stats)
    printf "%-6s %10s %8s\n" port mget_calls keys
    for p in $(masters); do
      mget=$(rcli -h host.docker.internal -p "$p" info commandstats | grep -o 'cmdstat_mget:calls=[0-9]*' | cut -d= -f2 || true)
      printf "%-6s %10s %8s\n" "$p" "${mget:-0}" "$(rcli -h host.docker.internal -p "$p" dbsize)"
    done
    ;;
  monitor)
    trap 'kill 0' INT
    for p in $(masters); do
      docker exec cb-redis-node-1 redis-cli -h host.docker.internal -p "$p" monitor | sed -u "s/^/[$p] /" &
    done
    wait
    ;;
  *)
    sed -n '2,6p' "$0"; exit 1
    ;;
esac
