package com.microservices.bootstrap.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JvmDiagnosticsLogger
 *
 * Logs JVM and application diagnostics to stdout on a schedule.
 * Designed to be dropped into any Spring Boot app regardless of stack.
 *
 * Compatible with:
 *   - Spring MVC and Spring Webflux
 *   - Apps with or without HikariCP (DB connection pool)
 *   - Apps with or without Reactor Netty (WebClient / Webflux)
 *
 * If a metric does not exist (e.g. no HikariCP, no Reactor Netty),
 * MeterRegistry returns 0.0 silently — no exceptions, no noise.
 *
 * Behaviour:
 *   - Warning checks run every 60 sec (cheap — counter reads only)
 *   - Full dump runs every 3 min, or immediately if any warning threshold crossed
 *
 * Required dependency (virtually always present in production Spring Boot apps):
 *   <dependency>
 *       <groupId>org.springframework.boot</groupId>
 *       <artifactId>spring-boot-starter-actuator</artifactId>
 *   </dependency>
 *
 * Enable Reactor Netty metrics in application.yml if using Webflux / WebClient:
 *   management:
 *     metrics:
 *       enable:
 *         reactor.netty: true
 *
 * Sample log output (normal operation):
 *   diag.memory heap.used_mb=68 heap.max_mb=384 heap.pct=17% direct.used_mb=8 ...
 *   diag.threads total=39 runnable=23 blocked=0 netty.event_loop.total=12 netty.event_loop.blocked_or_waiting=0
 *   diag.reactor.total active=12.0 idle=3.0 pending=0.0 max=100.0
 *   diag.reactor.pool pool=https://westpac.com active=10.0 idle=2.0 pending=0.0 max=100.0 pct=10%
 *   diag.reactor.pool pool=https://anz.com active=2.0 idle=1.0 pending=0.0 max=100.0 pct=2%
 *   diag.requests req_per_sec=28.4 peak_req_per_sec=31.2 avg.response_sec=0.823 max.response_sec=13.421 littles_law.connections_needed=419
 */
@Component
@EnableScheduling
public class JvmDiagnosticsLogger {

   private static final Logger log = LoggerFactory.getLogger(JvmDiagnosticsLogger.class);

   // ── Tunable thresholds ────────────────────────────────────────────────────
   private static final int FULL_DUMP_INTERVAL_MINUTES = 3;   // full dump every N min under normal conditions
   private static final int DIRECT_MEMORY_WARN_PCT     = 85;  // Netty off-heap warn threshold (%)
   private static final int HEAP_WARN_PCT              = 85;  // Java heap warn threshold (%)
   private static final int CONNECTION_POOL_WARN_PCT   = 80;  // Reactor Netty per-pool warn threshold (%)
   private static final int HIKARI_POOL_WARN_PCT       = 80;  // HikariCP pool warn threshold (%)
   private static final int HIKARI_PENDING_WARN        = 1;   // HikariCP pending connections warn threshold

   private final MeterRegistry meterRegistry;

   private Instant lastFullDump = Instant.EPOCH; // EPOCH ensures first tick always does a full dump

   // Request rate tracking — delta between ticks gives rolling req/sec
   private long    lastRequestCount = 0;
   private Instant lastCountTime    = Instant.now();
   private double  peakReqPerSec    = 0.0; // never resets — captures worst case ever seen

   public JvmDiagnosticsLogger(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
   }

   // =========================================================
   // MAIN TICK — every 3 minutes unless there is any warning. Modify as required.
   // =========================================================
   @Scheduled(fixedRate = 60_000)
   public void logDiagnostics() {
      boolean anyWarning = isDirectMemoryHigh()
              || isHeapHigh()
              || isConnectionPoolHigh()
              || isHikariPoolHigh();

      boolean dueForDump = Duration.between(lastFullDump, Instant.now())
              .toMinutes() >= FULL_DUMP_INTERVAL_MINUTES;

      if (!anyWarning && !dueForDump) return;

      logMemoryDiagnostics();
      logThreadDiagnostics();
      logReactorNettyMetrics();
      // logHikariMetrics();
      logRequestRate();

      lastFullDump = Instant.now();
   }

   // =========================================================
   // WARNING CHECKS
   // Run every tick — cheap counter reads, no stack walking
   // Each independently triggers an early full dump if threshold crossed
   // =========================================================

   /**
    * Netty off-heap I/O buffers — most critical metric for Webflux.
    * Climbs when in-flight requests accumulate faster than they complete.
    * Returns false quietly if Reactor Netty is not present.
    */
   private boolean isDirectMemoryHigh() {
      for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
         if (pool.getName().equals("direct") && pool.getTotalCapacity() > 0) {
            long pct = pool.getMemoryUsed() * 100 / pool.getTotalCapacity();
            if (pct > DIRECT_MEMORY_WARN_PCT) {
               log.warn("diag.memory.alert DIRECT MEMORY above {}% — " +
                          "used={}MB max={}MB — Netty buffer leak likely",
                          DIRECT_MEMORY_WARN_PCT,
                          mb(pool.getMemoryUsed()),
                          mb(pool.getTotalCapacity()));
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Java heap — objects, Spring beans, response bodies buffered in memory.
    * Climbs with memory leaks or large response body accumulation.
    */
   private boolean isHeapHigh() {
      MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
      long pct = pct(heap.getUsed(), heap.getMax());
      if (pct > HEAP_WARN_PCT) {
         log.warn("diag.memory.alert HEAP above {}% — used={}MB max={}MB",
                 HEAP_WARN_PCT,
                 mb(heap.getUsed()),
                 mb(heap.getMax()));
         return true;
      }
      return false;
   }

   /**
    * Reactor Netty HTTP connection pool — checked per pool (per BaseClient subclass).
    * Aggregate check can mask a single exhausted pool hidden behind healthy pools.
    * Example: WestpacClient at 95% + ANZClient at 5% = aggregate 50% — no alert fired.
    * Per-pool check catches WestpacClient at 95% directly.
    * active climbing = backend slow, holding connections open.
    * pending > 0 = pool fully exhausted — AbortedException imminent.
    * Returns false quietly if Reactor Netty / WebClient is not present.
    */
   private boolean isConnectionPoolHigh() {
      boolean[] warned = {false};

      meterRegistry.find("reactor.netty.connection.provider.active.connections")
              .gauges()
              .forEach(gauge -> {
                 String poolName = gauge.getId().getTag("id");
                 double active   = gauge.value();
                 double max      = gaugeValue("reactor.netty.connection.provider.max.connections", poolName);
                 double pending  = gaugeValue("reactor.netty.connection.provider.pending.connections", poolName);

                 if (max > 0 && active / max * 100 > CONNECTION_POOL_WARN_PCT) {
                    log.warn("diag.reactor.alert pool={} above {}% — " +
                                    "active={} max={} pending={} — backend may be slow",
                            poolName, CONNECTION_POOL_WARN_PCT, active, max, pending);
                    warned[0] = true;
                 }
                 if (pending > 0) {
                    log.warn("diag.reactor.alert pool={} {} requests PENDING — " +
                            "pool exhausted — AbortedException imminent", poolName, pending);
                    warned[0] = true;
                 }
              });

      return warned[0];
   }

   /**
    * HikariCP DB connection pool.
    * active climbing = DB queries slow or connection leak.
    * pending > 0 = all connections busy, threads BLOCKED waiting for DB — immediately dangerous.
    * Returns false quietly if HikariCP is not present.
    */
   private boolean isHikariPoolHigh() {
      double active  = gaugeValue("hikaricp.connections.active");
      double max     = gaugeValue("hikaricp.connections.max");
      double pending = gaugeValue("hikaricp.connections.pending");
      double timeout = counterValue("hikaricp.connections.timeout");

      if (max > 0 && active / max * 100 > HIKARI_POOL_WARN_PCT) {
         log.warn("diag.hikari.alert HikariCP pool above {}% — " +
                         "active={} max={} pending={} timeouts={}",
                 HIKARI_POOL_WARN_PCT, active, max, pending, timeout);
         return true;
      }
      if (pending >= HIKARI_PENDING_WARN) {
         log.warn("diag.hikari.alert {} threads PENDING DB connection — " +
                 "pool exhausted — DB may be slow or connection leak", pending);
         return true;
      }
      if (timeout > 0) {
         log.warn("diag.hikari.alert {} HikariCP connection timeouts recorded — " +
                 "DB unreachable or pool too small", timeout);
         return true;
      }
      return false;
   }

   // =========================================================
   // 1. MEMORY DIAGNOSTICS
   // Heap, nonheap (metaspace), direct (Netty off-heap), mapped, GC stats
   // =========================================================
   private void logMemoryDiagnostics() {
      MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
      MemoryUsage  heap    = memBean.getHeapMemoryUsage();
      MemoryUsage  nonHeap = memBean.getNonHeapMemoryUsage();

      long directUsed = 0, directMax = 0, mappedUsed = 0;
      for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
         if (pool.getName().equals("direct")) {
            directUsed = pool.getMemoryUsed();
            directMax  = pool.getTotalCapacity();
         } else if (pool.getName().equals("mapped")) {
            mappedUsed = pool.getMemoryUsed();
            // mapped has no configurable ceiling — informational only
            // non-zero only if app uses FileChannel.map() — rare in REST API apps
         }
      }

      String gcStats = ManagementFactory.getGarbageCollectorMXBeans().stream()
              .map(gc -> gc.getName()
                      + " count=" + gc.getCollectionCount()
                      + " time="  + gc.getCollectionTime() + "ms")
              .collect(Collectors.joining(" | "));

      log.info("Diag.memory: " +
                      "heap.used_mb={} heap.committed_mb={} heap.max_mb={} heap.pct={}% | " +
                      "nonheap.used_mb={} nonheap.committed_mb={} | " +
                      "direct.used_mb={} direct.max_mb={} direct.pct={}% | " +
                      "mapped.used_mb={} | " +
                      "gc=[{}]",
              mb(heap.getUsed()), mb(heap.getCommitted()), mb(heap.getMax()),
              pct(heap.getUsed(), heap.getMax()),
              mb(nonHeap.getUsed()), mb(nonHeap.getCommitted()),
              mb(directUsed), mb(directMax), pct(directUsed, directMax),
              mb(mappedUsed),
              gcStats
      );
   }

   // =========================================================
   // 2. THREAD DIAGNOSTICS
   // JVM thread states — catches deadlocks and blocking calls on Netty event loop
   // Full stack trace only logged when problematic threads detected
   //
   // Webflux:    thread count stays LOW and flat (8-16) — that's normal
   //             danger = reactor-http-nio threads BLOCKED or WAITING
   // Spring MVC: thread count IS the key metric
   //             danger = large number of BLOCKED threads = thread pool exhaustion
   // =========================================================
   private void logThreadDiagnostics() {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      ThreadInfo[] threads    = threadBean.dumpAllThreads(true, true);

      long blocked      = count(threads, Thread.State.BLOCKED);
      long waiting      = count(threads, Thread.State.WAITING);
      long timedWaiting = count(threads, Thread.State.TIMED_WAITING);
      long runnable     = count(threads, Thread.State.RUNNABLE);

      long nettyTotal   = Arrays.stream(threads)
              .filter(t -> t.getThreadName().contains("reactor-http-nio"))
              .count();
      long nettyBlocked = Arrays.stream(threads)
              .filter(t -> t.getThreadName().contains("reactor-http-nio"))
              .filter(t -> t.getThreadState() == Thread.State.BLOCKED
                      || t.getThreadState() == Thread.State.WAITING)
              .count();

      log.info("Diag.threads: " +
                      "total={} runnable={} blocked={} waiting={} timed_waiting={} | " +
                      "netty.event_loop.total={} netty.event_loop.blocked_or_waiting={}",
              threads.length, runnable, blocked, waiting, timedWaiting,
              nettyTotal, nettyBlocked
      );

      // Any blocked thread is worth investigating
      if (blocked > 0) {
         log.warn("diag.threads.alert {} BLOCKED threads detected", blocked);
         Arrays.stream(threads)
                 .filter(t -> t.getThreadState() == Thread.State.BLOCKED)
                 .forEach(t -> log.warn("diag.threads.blocked thread={} lock={}\n{}",
                         t.getThreadName(), t.getLockName(), formatStackTrace(t)));
      }

      // reactor-http-nio BLOCKED = blocking call on Netty event loop = critical in Webflux
      // In Spring MVC, nettyBlocked is always 0 since no Netty threads exist — harmless
      if (nettyBlocked > 0) {
         log.warn("diag.threads.alert {} reactor-http-nio threads BLOCKED — " +
                         "blocking call on Netty event loop detected — app may freeze entirely",
                 nettyBlocked);
         Arrays.stream(threads)
                 .filter(t -> t.getThreadName().contains("reactor-http-nio"))
                 .filter(t -> t.getThreadState() == Thread.State.BLOCKED
                         || t.getThreadState() == Thread.State.WAITING)
                 .forEach(t -> log.warn("diag.threads.netty.blocked thread={}\n{}",
                         t.getThreadName(), formatStackTrace(t)));
      }
   }

   // =========================================================
   // 3. REACTOR NETTY CONNECTION POOL METRICS
   // HTTP connections from your app to backend APIs (one pool per BaseClient subclass)
   //
   // Logs aggregate total first, then one line per pool (per backend host).
   // Per-pool breakdown is critical — aggregate totals can mask a single
   // exhausted pool hidden behind other healthy pools.
   //
   // Key difference from thread diagnostics:
   //   Webflux: 500 active HTTP connections on just 8 threads — completely normal
   //   Threads stay flat, connections fluctuate with backend response time
   //   Pool exhaustion catches backend slowness that thread counts never will
   //
   // Logs all zeros silently if Reactor Netty / WebClient is not present
   // =========================================================
   private void logReactorNettyMetrics() {
      // Aggregate totals across all pools
      double totalActive  = gaugeValue("reactor.netty.connection.provider.active.connections");
      double totalIdle    = gaugeValue("reactor.netty.connection.provider.idle.connections");
      double totalPending = gaugeValue("reactor.netty.connection.provider.pending.connections");
      double totalMax     = gaugeValue("reactor.netty.connection.provider.max.connections");
      double errors       = counterValue("reactor.netty.http.server.errors");
      double bytesIn      = counterValue("reactor.netty.http.client.data.received");
      double bytesOut     = counterValue("reactor.netty.http.client.data.sent");

      log.info("Diag.reactor.total: " +
                      "active={} idle={} pending={} max={} | " +
                      "server.errors={} bytes.in={} bytes.out={}",
              totalActive, totalIdle, totalPending, totalMax,
              errors, bytesIn, bytesOut
      );

      // Per-pool breakdown — one line per backend host / BaseClient subclass
      // pool name = ConnectionProvider.builder(url) name set in BaseClient
      meterRegistry.find("reactor.netty.connection.provider.active.connections")
              .gauges()
              .forEach(gauge -> {
                 String poolName = gauge.getId().getTag("name");
                 double active   = gauge.value();
                 double max      = gaugeValue("reactor.netty.connection.provider.max.connections", poolName);
                 double pending  = gaugeValue("reactor.netty.connection.provider.pending.connections", poolName);
                 double idle     = gaugeValue("reactor.netty.connection.provider.idle.connections", poolName);
                 double pct      = max > 0 ? active / max * 100 : 0;

                 log.info("diag.reactor.pool pool={} active={} idle={} pending={} max={} pct={}%",
                         poolName, active, idle, pending, max,
                         String.format("%.0f", pct)
                 );
              });
   }

   // =========================================================
   // 4. HIKARICP DB CONNECTION POOL METRICS
   // DB connections from your app to database
   //
   // Key difference from Reactor Netty pool:
   //   Reactor Netty = HTTP connections to backend API (async, non-blocking)
   //   HikariCP      = DB connections (blocking — each holds a thread while waiting)
   //   HikariCP pending > 0 means threads are BLOCKED waiting for DB — immediately dangerous
   //
   // Logs all zeros silently if HikariCP is not present
   // =========================================================
   private void logHikariMetrics() {
      double active   = gaugeValue("hikaricp.connections.active");
      double idle     = gaugeValue("hikaricp.connections.idle");
      double pending  = gaugeValue("hikaricp.connections.pending");
      double max      = gaugeValue("hikaricp.connections.max");
      double min      = gaugeValue("hikaricp.connections.min");
      double timeout  = counterValue("hikaricp.connections.timeout");
      double acquired = gaugeValue("hikaricp.connections.acquire");
      double creation = gaugeValue("hikaricp.connections.creation");

      log.info("Diag.hikari: " +
                      "connections.active={} connections.idle={} connections.pending={} " +
                      "connections.max={} connections.min={} | " +
                      "timeouts={} acquire_ms={} creation_ms={}",
              active, idle, pending,
              max, min,
              timeout, acquired, creation
      );
   }

   // =========================================================
   // 5. REQUEST RATE
   // Computes rolling req/sec using delta between ticks.
   //
   // http.server.requests is a cumulative counter — never resets.
   // Delta between two readings / elapsed seconds = req/sec for that window.
   //
   // req_per_sec:      requests/sec in the last 60 sec window
   // peak_req_per_sec: highest req/sec ever observed since startup — never resets
   //                   use this for Little's Law pool sizing (worst case, not average)
   //
   // Little's Law: maxConnections needed = peak_req_per_sec x max.response_sec
   // Example: peak=30 req/sec x max=13 sec response = 390 connections needed
   //
   // Logs zeros silently if http.server.requests metric is not present.
   // =========================================================
   private void logRequestRate() {
      Timer timer = meterRegistry.find("http.server.requests").timer();

      if (timer == null) {
         log.info("Diag.requests: req_per_sec=0.0 peak_req_per_sec=0.0 " +
                 "avg.response_sec=0.000 max.response_sec=0.000 Littles_law.connections_needed=0");
         return;
      }

      long    currentCount = timer.count();   // cumulative requests completed since startup
      Instant now          = Instant.now();

      // Requests completed since last tick (delta removes cumulative bias)
      long   delta      = currentCount - lastRequestCount;
      // Actual elapsed time — more accurate than hardcoding 60 sec
      // (@Scheduled fires slightly late under JVM load)
      double elapsedSec = Duration.between(lastCountTime, now).toMillis() / 1000.0;
      // Requests per second for this 60 sec window
      double reqPerSec  = elapsedSec > 0 ? delta / elapsedSec : 0;

      // Track peak — never resets, always reflects worst case ever seen since startup
      if (reqPerSec > peakReqPerSec) {
         peakReqPerSec = reqPerSec;
      }

      // Cumulative averages across all requests since startup
      double avgResponseSec = currentCount > 0
              ? timer.totalTime(TimeUnit.SECONDS) / currentCount : 0;
      // max resets periodically in Micrometer (default every 2 min) — reflects recent worst case
      double maxResponseSec = timer.max(TimeUnit.SECONDS);

      // Little's Law: use peak req/sec x max response time = worst-case connections needed
      long connectionsNeeded = Math.round(peakReqPerSec * maxResponseSec);

      log.info("Diag.requests: " +
                      "req_per_sec={} peak_req_per_sec={} " +
                      "avg.response_sec={} max.response_sec={} " +
                      "littles_law.connections_needed={}",
              String.format("%.1f", reqPerSec),
              String.format("%.1f", peakReqPerSec),
              String.format("%.3f", avgResponseSec),
              String.format("%.3f", maxResponseSec),
              connectionsNeeded
      );

      // Update state for next tick
      lastRequestCount = currentCount;
      lastCountTime    = now;
   }

   // =========================================================
   // HELPERS
   // =========================================================

   private long mb(long bytes)           { return bytes / 1024 / 1024; }
   private long pct(long used, long max) { return max <= 0 ? 0 : used * 100 / max; }

   private long count(ThreadInfo[] threads, Thread.State state) {
      return Arrays.stream(threads)
              .filter(t -> t.getThreadState() == state)
              .count();
   }

   /** Sum all gauges matching name across all pools / tags */
   private double gaugeValue(String name) {
      return meterRegistry.find(name).gauges().stream()
              .mapToDouble(g -> g.value())
              .sum();
   }

   /** Sum gauges matching name filtered to a specific pool by its "id" tag */
   private double gaugeValue(String name, String poolName) {
      return meterRegistry.find(name)
              .tag("id", poolName)
              .gauges().stream()
              .mapToDouble(g -> g.value())
              .sum();
   }

   private double counterValue(String name) {
      return meterRegistry.find(name).counters().stream()
              .mapToDouble(c -> c.count())
              .sum();
   }

   private String formatStackTrace(ThreadInfo threadInfo) {
      return Arrays.stream(threadInfo.getStackTrace())
              .limit(10)
              .map(StackTraceElement::toString)
              .collect(Collectors.joining("\n  at ", "  at ", ""));
   }
}
