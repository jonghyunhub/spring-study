import http from 'k6/http';
import { check } from 'k6';

export const CORE_API  = 'http://localhost:8080';
export const STUB_API  = 'http://localhost:8081';

/**
 * stub-service 모드 전환
 * @param {'fail' | 'slow' | 'recover'} mode
 */
export function setStubMode(mode) {
  const res = http.post(`${STUB_API}/control/${mode}`);
  console.log(`[stub] mode → ${mode} (HTTP ${res.status})`);
}

/** 현재 stub-service 모드 조회 */
export function getStubStatus() {
  const res = http.get(`${STUB_API}/control/status`);
  console.log(`[stub] status: ${res.body}`);
  return res.body;
}

/**
 * /products/{id} 호출 후 응답 상태 코드별 체크
 * - 200: 정상
 * - 502: 외부 서비스 실패 (CB CLOSED이지만 stub이 오류 반환)
 * - 503: CB OPEN — 호출 자체가 차단됨
 */
export function callProduct(id = 1) {
  const res = http.get(`${CORE_API}/products/${id}`);
  check(res, {
    '200 OK (정상 응답)':         (r) => r.status === 200,
    '502 Bad Gateway (외부 오류)': (r) => r.status === 502,
    '503 Service Unavailable (CB OPEN — 차단)': (r) => r.status === 503,
  });
  return res;
}

/** CB 상태 조회 (/actuator/health) */
export function getCBState() {
  const res = http.get(`${CORE_API}/actuator/health`);
  if (res.status === 200) {
    const body = JSON.parse(res.body);
    const state = body?.components?.circuitBreakers?.details?.['stub-service']?.details?.state ?? 'UNKNOWN';
    console.log(`[CB] state: ${state}`);
    return state;
  }
  return 'UNKNOWN';
}
