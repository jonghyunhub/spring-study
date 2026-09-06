import { options, runBenchmark } from "./lib/benchmark.js";

export default function () {
  runBenchmark("mysql-jdbc");
}

export { options };
