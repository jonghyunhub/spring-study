/**
 * 시나리오 3: OPEN → HALF_OPEN → CLOSED 복구 흐름
 *
 * 목표
 *   CB가 OPEN 된 이후 stub-service를 복구하고,
 *   wait-duration-in-open-state(10초) 경과 → HALF_OPEN 진입 →
 *   3건 성공 → CLOSED 복구되는 전체 흐름을 확인합니다.
 *
 * 타임라인
 *   0s  ~ 20s : [phase-1] FAIL 모드 + 5 VU — CB OPEN 유도
 *   20s        [switch]  stub-service를 recover로 전환 (1회성 VU)
 *   20s ~ 30s : [phase-2] OPEN 상태 확인 — 503 차단 검증
 *   35s ~ 55s : [phase-3] HALF_OPEN → CLOSED 복구 확인
 *               (wait-duration 10s + 여유 5s = 35s부터 HALF_OPEN 진입)
 *
 * CB 설정 (application.yml 기준)
 *   wait-duration-in-open-state                  : 10s
 *   permitted-number-of-calls-in-half-open-state : 3
 *
 * 실행
 *   k6 run k6/scenarios/03-recovery.js
 */

import { sleep } from 'k6';
import { setStubMode, callProduct, getCBState } from '../lib/helpers.js';

export const options = {
  scenarios: {
    // PHASE 1: FAIL 모드로 반복 호출 → CB OPEN 유도
    'phase-1-induce-open': {
      executor: 'constant-vus',
      vus: 5,
      duration: '20s',
      startTime: '0s',
      exec: 'induceOpen',
    },

    // 20s에 stub-service를 recover로 전환 (1회성)
    'switch-to-recover': {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: '20s',
      exec: 'switchToRecover',
    },

    // PHASE 2: recover 전환 직후 → 아직 OPEN 상태 (503 차단) 확인
    'phase-2-verify-open': {
      executor: 'constant-vus',
      vus: 2,
      duration: '10s',
      startTime: '21s',
      exec: 'verifyOpen',
    },

    // PHASE 3: wait-duration(10s) 경과 → HALF_OPEN → CLOSED 복구 확인
    'phase-3-verify-recovery': {
      executor: 'constant-vus',
      vus: 3,
      duration: '20s',
      startTime: '35s',
      exec: 'verifyRecovery',
    },
  },

  thresholds: {
    // PHASE 3에서 200이 1건 이상 발생해야 통과 — CLOSED 복구 여부 검증
    'checks{scenario:phase-3-verify-recovery}': ['rate>0'],
  },
};

export function setup() {
  console.log('=== 시나리오 3: OPEN → HALF_OPEN → CLOSED 복구 흐름 ===');
  setStubMode('fail');
  sleep(1);
  getCBState();
}

/** PHASE 1: FAIL 모드, CB OPEN 유도 */
export function induceOpen() {
  callProduct(1);
  sleep(0.3);
}

/** 20s 시점에 1회 실행 — stub-service를 정상 모드로 전환 */
export function switchToRecover() {
  console.log('[switch] stub-service → recover 모드 전환');
  setStubMode('recover');
  getCBState();
}

/** PHASE 2: stub은 회복됐지만 CB는 아직 OPEN → 503 차단 확인 */
export function verifyOpen() {
  callProduct(1);
  sleep(1);
}

/** PHASE 3: HALF_OPEN 이후 정상 응답 → CLOSED 복구 확인 */
export function verifyRecovery() {
  callProduct(1);
  sleep(1);
}

export function teardown() {
  console.log('[teardown] 최종 CB 상태 확인');
  getCBState();
}
