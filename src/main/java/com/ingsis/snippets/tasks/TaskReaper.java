package com.ingsis.snippets.tasks;

import com.ingsis.snippets.snippet.service.RuleApplier;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-encola los snippets que siguen PENDING pasado {@code TASK_TIMEOUT}: cubre que Redis pierda una
 * tarea o que haya fallado la publicación. Después de {@code max-requeues} quedan FAILED.
 */
@Component
public class TaskReaper {

  private static final Logger log = LoggerFactory.getLogger(TaskReaper.class);

  private final RuleApplier applier;

  public TaskReaper(RuleApplier applier) {
    this.applier = applier;
  }

  @Scheduled(
      fixedDelayString = "${tasks.reaper-interval:60s}",
      initialDelayString = "${tasks.reaper-interval:60s}")
  public void reap() {
    reap(Instant.now());
  }

  int reap(Instant now) {
    try {
      int requeued = applier.requeueStalled(now);
      if (requeued > 0) {
        log.info("Reaper re-enqueued {} stalled tasks", requeued);
      }
      return requeued;
    } catch (RuntimeException ex) {
      log.warn("Reaper run failed; will retry on the next tick", ex);
      return 0;
    }
  }
}
