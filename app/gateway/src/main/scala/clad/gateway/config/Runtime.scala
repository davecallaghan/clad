package clad.gateway.config

import java.nio.file.Path

/** Every number the gateway uses, from the adopter's runtime.yaml (no defaults: a missing key is an error). */
final case class Runtime(chainLockKey: Long, upstreamRetries: Int, upstreamTimeoutSeconds: Int, upstreamRetryWaitMillis: Int,
                          latencyBudgetMillis: Int, maxRetries: Int, retrySimilarity: Double, groundingIgnoreBelow: Double,
                          rocBackgroundWorkers: Int, healthProbeTimeoutMillis: Int)

object Runtime:
  def load(path: Path): Either[String, Runtime] = Loading.guard(path)(fromJson(YamlJson.read(path)))

  def fromJson(v: ujson.Value): Either[String, Runtime] =
    Loading.guard(Path.of("runtime.yaml")) {
      def num(k: String): Either[String, Double] = v.obj.get(k).collect { case ujson.Num(n) => n }.toRight(s"runtime.yaml: $k is required")
      for
        lock <- num("chain_lock_key")
        retries <- num("upstream_retries")
        wait <- num("upstream_timeout_seconds")
        pause <- num("upstream_retry_wait_ms")
        budget <- num("latency_budget_ms")
        maxRetries <- num("max_retries")
        similarity <- num("retry_similarity")
        ignoreBelow <- num("grounding_ignore_below")
        workers <- num("roc_background_workers")
        healthTimeout <- num("health_probe_timeout_ms")
      yield Runtime(lock.toLong, retries.toInt, wait.toInt, pause.toInt, budget.toInt, maxRetries.toInt, similarity, ignoreBelow,
        workers.toInt, healthTimeout.toInt)
    }
