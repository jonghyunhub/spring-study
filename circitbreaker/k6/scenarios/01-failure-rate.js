/**
 * 시나리오 1: 실패율 기반 OPEN 전환
 *
 * 목표
 *   stub-service를 FAIL 모드로 전환한 뒤 반복 호출하여
 *   실패율이 50%를 초과하면 CB가 OPEN으로 전환되는 것을 확인합니다.
 *
 * 기대 흐름
 *   1~5건  : 502 Bad Gateway  (CB CLOSED, stub이 500 반환 → 실패 카운트)
 *   6건~   : 503 Unavailable  (CB OPEN, 호출 자체 차단)
 *
 * CB 설정 (application.yml 기준)
 *   minimum-number-of-calls : 5  (5건 이상 쌓여야 실패율 계산 시작)
 *   failure-rate-threshold  : 50 (50% 초과 시 OPEN)
 *   sliding-window-size     : 10 (최근 10건 기준)
 *
 * 실행
 *   k6 run k6/scenarios/01-failure-rate.js
 */

import { sleep } from 'k6';
import { setStubMode, callProduct, getCBState } from '../lib/helpers.js';

export const options = {
  stages: [
    { duration: '5s',  target: 5  },  // VU 5명으로 ramp-up
    { duration: '30s', target: 5  },  // 30초 지속 — OPEN 전환 유도
    { duration: '5s',  target: 0  },  // ramp-down
  ],
  thresholds: {
    // 503이 1건 이상 발생해야 통과 — CB OPEN 전환 여부 검증
    'checks{check:503 Service Unavailable (CB OPEN — 차단)}': ['rate>0'],
  },
};

export function setup() {
  console.log('=== 시나리오 1: 실패율 기반 OPEN 전환 ===');
  setStubMode('fail');
  sleep(1);
  getCBState();
}

export default function () {
  callProduct(1);
  sleep(0.5);
}

export function teardown() {
  console.log('[teardown] stub-service를 정상 모드로 복구합니다.');
  setStubMode('recover');
  getCBState();
}
