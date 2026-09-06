import { options, runBenchmark } from "./lib/benchmark.js";

export default function () {
  runBenchmark("redis-string");
}

export { options };
