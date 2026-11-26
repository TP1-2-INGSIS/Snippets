package com.ingsis.snippets.tasks;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * XADD de las tareas al stream de trabajo. Dentro de una transacción publica después del commit,
 * así un worker nunca ve una tarea de un estado que se revirtió. Un fallo al publicar no tira el
 * request: el snippet queda PENDING y el reaper lo re-encola.
 */
@Component
public class TaskPublisher {

  private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);

  private final StringRedisTemplate redis;

  private final ObjectMapper mapper;

  private final TasksProperties properties;

  public TaskPublisher(StringRedisTemplate redis, ObjectMapper mapper, TasksProperties properties) {
    this.redis = redis;
    this.mapper = mapper;
    this.properties = properties;
  }

  public void publishAfterCommit(List<TaskEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              events.forEach(TaskPublisher.this::publish);
            }
          });
    } else {
      events.forEach(this::publish);
    }
  }

  /**
   * @return si se pudo publicar
   */
  boolean publish(TaskEvent event) {
    try {
      redis
          .opsForStream()
          .add(
              StreamRecords.string(Map.of("data", mapper.writeValueAsString(event)))
                  .withStreamKey(properties.stream()));
      return true;
    } catch (RuntimeException ex) {
      log.warn("Could not publish task {} for snippet {}", event.taskId(), event.snippetId(), ex);
      return false;
    }
  }
}
