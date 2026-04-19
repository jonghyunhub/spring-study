/**
 * 시나리오 2: slow call 기반 OPEN 전환
 *
 * 목표
 *   stub-service를 SLOW 모드(3초 지연)로 전환한 뒤 반복 호출하여
 *   slow call 비율이 50%를 초과하면 CB가 OPEN으로 전환되는 것을 확인합니다.
 *
 * 기대 흐름
 *   1~5건  : 200 OK (3초 지연) — slow call로 카운트됨
 *   6건~   : 503 Unavailable  (slow call 비율 50% 초과 → CB OPEN)
 *
 * CB 설정 (application.yml 기준)
 *   slow-call-duration-threshold : 2s  (2초 이상 = slow call)
 *   slow-call-rate-threshold     : 50  (50% 초과 시 OPEN)
 *   FeignConfig read-timeout     : 5s  (slow call이 timeout 전에 응답 받아야 카운트됨)
 *
 * 실행
 *   # 빌트인 웹 대시보드 (http://localhost:5665)
 *   K6_WEB_DASHBOARD=true k6 run k6/scenarios/02-slow-call.js
 *
 *   # Grafana 연동 (기존 Prometheus로 push)
 *   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
 *   k6 run --out experimental-prometheus-rw k6/scenarios/02-slow-call.js
 */

import { sleep, check } from 'k6';
import http from 'k6/http';
import { setStubMode, getCBState, CORE_API } from '../lib/helpers.js';

export const options = {
  stages: [
    { duration: '5s',   target: 3  },  // VU 3명 (slow call이라 VU 적어도 충분)
    { duration: '110s', target: 3  },  // 110초 지속 — 3초 지연 × 반복
    { duration: '5s',   target: 0  },
  ],
  thresholds: {
    // 응답시간 p95가 2초 이상이어야 통과 — slow call 발생 여부 검증
    'http_req_duration{url:http://localhost:8080/products/1}': ['p(95)>2000'],
    // 503이 1건 이상 발생해야 통과 — CB OPEN 전환 여부 검증
    'checks{check:503 Service Unavailable (CB OPEN — 차단)}': ['rate>0'],
  },
};

export function setup() {
  console.log('=== 시나리오 2: slow call 기반 OPEN 전환 ===');
  setStubMode('slow');
  sleep(1);
  getCBState();
}

export default function () {
  const res = http.get(`${CORE_API}/products/1`);
  check(res, {
    '200 OK (slow 응답)':           (r) => r.status === 200,
    '503 Service Unavailable (CB OPEN — 차단)': (r) => r.status === 503,
  });
  // slow call 자체가 3초이므로 추가 sleep 불필요
}

export function teardown() {
  console.log('[teardown] stub-service를 정상 모드로 복구합니다.');
  setStubMode('recover');
  getCBState();
}
