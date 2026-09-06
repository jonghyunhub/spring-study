import { options, runBenchmark } from "./lib/benchmark.js";

export default function () {
  runBenchmark("mysql");
}

export { options };
