// 04. Redis Cluster MGET 분할과 L1(Caffeine) 효과 확인
//
// 상품 50개 요청을 4번 보내고, 요청마다 Lettuce 가 보낸 MGET 수를 출력한다.
//   1) findFromCache          50개        → MGET ≈ 50  (slot 마다 MGET 1개로 분할)
//   2) findFromLayeredCache   50개 (처음)  → MGET ≈ 50  (L1 이 비어 있어 원본과 같음)
//   3) findFromLayeredCache   같은 50개    → MGET = 0   (전부 L1 hit)
//   4) findFromLayeredCache   절반 겹침    → MGET ≈ 25  (L1 miss 25개만 Redis 로)
//
// 사전 조건: MySQL + docker-compose-cluster.yml 기동, core-api 를 cluster 프로필로 기동
// 실행: k6 run k6/scenarios/04-layered-cache.js   (docker/cluster.sh monitor 를 같이 켜두면 MGET 을 눈으로 볼 수 있음)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { CORE_API } from '../lib/helpers.js';

const POOL = 10000;
const SIZE = 50;

export const options = { vus: 1, iterations: 1 };

export function setup() {
  const res = http.post(`${CORE_API}/products/seed?count=${POOL}&ratio=10`);
  check(res, { 'seed 200': (r) => r.status === 200 });
}

export default function () {
  // 직전 실행의 L1(TTL 30s)과 겹치지 않도록 실행마다 다른 id 구간을 사용
  const base = Math.floor(Math.random() * (POOL - SIZE * 2));
  const first = range(base + 1, SIZE);
  const half = range(base + 1 + SIZE / 2, SIZE); // 앞 25개는 first 와 겹침

  const results = [
    ['1) cache   (50개)', mgetOf('/products', first), `≈ ${SIZE}`],
    ['2) layered (처음)', mgetOf('/products/layered-cache', first), `≈ ${SIZE}`],
    ['3) layered (같은 50개)', mgetOf('/products/layered-cache', first), '0'],
    ['4) layered (절반 겹침)', mgetOf('/products/layered-cache', half), `≈ ${SIZE / 2}`],
  ];

  console.log('\n요청                      MGET   기대값');
  results.forEach(([name, mget, expected]) => console.log(`${name.padEnd(24)} ${String(mget).padStart(5)}   ${expected}`));

  check(results, {
    'L1 hit 이면 MGET 0': (r) => r[2][1] === 0,
    '절반 겹치면 MGET 감소': (r) => r[3][1] < r[1][1],
  });
}

// 요청 1건을 보내고, 그 사이 Lettuce 가 보낸 MGET 수를 반환
function mgetOf(path, ids) {
  const before = lettuceMgetCount();
  const res = http.post(`${CORE_API}${path}`, JSON.stringify({ ids }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { [`${path} 200`]: (r) => r.status === 200 });
  sleep(0.5); // Lettuce 메트릭 반영 대기
  return lettuceMgetCount() - before;
}

function lettuceMgetCount() {
  const res = http.get(`${CORE_API}/actuator/metrics/lettuce.command.completion?tag=command:MGET`);
  if (res.status !== 200) return 0; // 아직 MGET 이 한 번도 기록되지 않은 경우 404
  return res.json().measurements.find((m) => m.statistic === 'COUNT').value;
}

function range(start, size) {
  return Array.from({ length: size }, (_, i) => start + i);
}
