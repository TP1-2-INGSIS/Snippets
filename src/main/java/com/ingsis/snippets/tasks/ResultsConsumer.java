package com.ingsis.snippets.tasks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumidor de {@code tasks:results} (grupo {@code snippet-service}, consumer = hostname).
 *
 * <p>Cada ciclo: procesa primero sus propias entradas pendientes (las que quedaron sin ACK antes de
 * un reinicio o un error), después las nuevas, y cada tanto hace XAUTOCLAIM de lo que otro consumer
 * dejó colgado más de {@code claim-idle}. El ACK va después del commit de {@link ResultProcessor}.
 * Si aplicar lanza una excepción no se hace ACK (se reintenta); a la {@code max-deliveries}-ésima
 * entrega se loguea y se descarta.
 */
@Component
public class ResultsConsumer implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ResultsConsumer.class);

  private static final int BATCH = 50;

  private final StringRedisTemplate redis;

  private final ObjectMapper mapper;

  private final ResultProcessor processor;

  private final TasksProperties properties;

  private final String consumerName;

  private final Map<String, Integer> failures = new HashMap<>();

  /**
   * Hasta cuándo no se reintenta una entrada que falló (backoff corto ante errores transitorios).
   */
  private final Map<String, Long> retryAt = new HashMap<>();

  private volatile boolean running;

  private Thread thread;

  private long lastClaim;

  public ResultsConsumer(
      StringRedisTemplate redis,
      ObjectMapper mapper,
      ResultProcessor processor,
      TasksProperties properties) {
    this.redis = redis;
    this.mapper = mapper;
    this.processor = processor;
    this.properties = properties;
    this.consumerName = hostname();
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      return "snippet-service-" + ProcessHandle.current().pid();
    }
  }

  String consumerName() {
    return consumerName;
  }

  @Override
  public synchronized void start() {
    if (running || !properties.consumerEnabled()) {
      return;
    }
    running = true;
    thread = new Thread(this::run, "results-consumer");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(5000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      thread = null;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void run() {
    while (running) {
      int handled = 0;
      try {
        ensureGroup();
        handled = pollOnce();
      } catch (RuntimeException ex) {
        log.warn("Results consumer cycle failed; will retry", ex);
      }
      if (handled == 0) {
        sleep(properties.pollInterval());
      }
    }
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      running = false;
    }
  }

  /** Crea el grupo desde el id 0, con MKSTREAM; si ya existe no hace nada. */
  void ensureGroup() {
    try {
      redis.execute(
          (org.springframework.data.redis.core.RedisCallback<String>)
              connection ->
                  connection
                      .streamCommands()
                      .xGroupCreate(
                          properties.resultsStream().getBytes(StandardCharsets.UTF_8),
                          properties.resultsGroup(),
                          ReadOffset.from("0"),
                          true));
    } catch (RuntimeException ex) {
      if (!String.valueOf(ex.getMessage()).contains("BUSYGROUP")
          && !String.valueOf(ex.getCause()).contains("BUSYGROUP")) {
        throw ex;
      }
    }
  }

  /**
   * Un ciclo: autoclaim (si toca), pendientes propias y nuevas.
   *
   * @return cuántas entradas se procesaron
   */
  int pollOnce() {
    autoClaimIfDue();
    Consumer consumer = Consumer.from(properties.resultsGroup(), consumerName);
    StreamReadOptions options = StreamReadOptions.empty().count(BATCH);
    int handled = handle(readFrom(consumer, options, ReadOffset.from("0")));
    handled += handle(readFrom(consumer, options, ReadOffset.lastConsumed()));
    return handled;
  }

  private List<MapRecord<String, Object, Object>> readFrom(
      Consumer consumer, StreamReadOptions options, ReadOffset offset) {
    List<MapRecord<String, Object, Object>> records =
        redis
            .opsForStream()
            .read(consumer, options, StreamOffset.create(properties.resultsStream(), offset));
    return records == null ? List.of() : records;
  }

  /** XAUTOCLAIM: se queda con las entradas de otros consumers inactivas más de claim-idle. */
  private void autoClaimIfDue() {
    long now = System.currentTimeMillis();
    if (now - lastClaim < properties.claimInterval().toMillis()) {
      return;
    }
    lastClaim = now;
    redis.execute(
        (org.springframework.data.redis.core.RedisCallback<Object>)
            connection ->
                connection.execute(
                    "XAUTOCLAIM",
                    properties.resultsStream().getBytes(StandardCharsets.UTF_8),
                    properties.resultsGroup().getBytes(StandardCharsets.UTF_8),
                    consumerName.getBytes(StandardCharsets.UTF_8),
                    String.valueOf(properties.claimIdle().toMillis())
                        .getBytes(StandardCharsets.UTF_8),
                    "0-0".getBytes(StandardCharsets.UTF_8),
                    "COUNT".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(BATCH).getBytes(StandardCharsets.UTF_8)));
  }

  private int handle(List<MapRecord<String, Object, Object>> records) {
    int handled = 0;
    for (MapRecord<String, Object, Object> record : records) {
      if (process(record)) {
        handled++;
      }
    }
    return handled;
  }

  /**
   * @return false si la entrada sigue en backoff y se salteó
   */
  private boolean process(MapRecord<String, Object, Object> record) {
    String id = record.getId().getValue();
    if (retryAt.getOrDefault(id, 0L) > System.currentTimeMillis()) {
      return false;
    }
    Object data = record.getValue().get("data");
    TaskResultEvent event;
    try {
      event = mapper.readValue(String.valueOf(data), TaskResultEvent.class);
    } catch (RuntimeException ex) {
      log.warn("Discarding malformed result {}", id, ex);
      ack(record.getId());
      return true;
    }
    try {
      processor.apply(event);
      failures.remove(id);
      retryAt.remove(id);
      ack(record.getId());
    } catch (RuntimeException ex) {
      // Redis counts every re-read of our own pending list as a delivery, so attempts are counted
      // here; Redis's count only seeds the first failure (e.g. entries delivered before a restart).
      Integer prior = failures.get(id);
      int failed = prior == null ? (int) Math.max(1, deliveryCount(record.getId())) : prior + 1;
      if (failed >= properties.maxDeliveries()) {
        log.error("Dropping result {} after {} deliveries", id, failed, ex);
        failures.remove(id);
        retryAt.remove(id);
        ack(record.getId());
      } else {
        failures.put(id, failed);
        retryAt.put(id, System.currentTimeMillis() + properties.retryBackoff().toMillis() * failed);
        log.warn("Applying result {} failed (delivery {}); will retry", id, failed, ex);
      }
    }
    return true;
  }

  private long deliveryCount(RecordId id) {
    PendingMessages pending =
        redis
            .opsForStream()
            .pending(
                properties.resultsStream(),
                properties.resultsGroup(),
                Range.just(id.getValue()),
                1L);
    return pending == null || pending.isEmpty() ? 0 : pending.get(0).getTotalDeliveryCount();
  }

  private void ack(RecordId id) {
    redis.opsForStream().acknowledge(properties.resultsStream(), properties.resultsGroup(), id);
  }
}
