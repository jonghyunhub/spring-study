import http from "k6/http";
import { check } from "k6";

export const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
export const COUNT = parsePositiveInt(__ENV.COUNT, 1000);
export const DISTRIBUTION = __ENV.DISTRIBUTION || "uniform";
export const HOT_RATIO = parseRatio(__ENV.HOT_RATIO, 0.8);
export const HOT_PERCENT = parseRatio(__ENV.HOT_PERCENT, 0.01);

export const options = {
  vus: parsePositiveInt(__ENV.VUS, 50),
  duration: __ENV.DURATION || "30s",
};

export function runBenchmark(path) {
  const id = pickId();
  const res = http.get(`${BASE_URL}/benchmark/${path}/${id}`, {
    tags: {
      name: `/benchmark/${path}/:id`,
    },
  });
  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}

function pickId() {
  if (DISTRIBUTION === "hot") {
    return pickHotKeyId();
  }

  return randomInt(1, COUNT);
}

function pickHotKeyId() {
  const hotCount = Math.max(1, Math.floor(COUNT * HOT_PERCENT));

  if (Math.random() < HOT_RATIO) {
    return randomInt(1, hotCount);
  }

  if (hotCount >= COUNT) {
    return randomInt(1, COUNT);
  }

  return randomInt(hotCount + 1, COUNT);
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function parsePositiveInt(value, fallback) {
  if (!value) return fallback;

  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function parseRatio(value, fallback) {
  if (!value) return fallback;

  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 1 ? parsed : fallback;
}
