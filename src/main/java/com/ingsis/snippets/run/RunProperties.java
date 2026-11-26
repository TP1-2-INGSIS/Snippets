package com.ingsis.snippets.run;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Interactive execution relay ({@code run.*}).
 *
 * @param idleTimeout the relay is closed with an error when nobody sends anything for this long. A
 *     safety net above Language's own limits (60 s waiting for an input, 5 min in total).
 */
@ConfigurationProperties("run")
public record RunProperties(Duration idleTimeout) {

  public RunProperties {
    if (idleTimeout == null) {
      idleTimeout = Duration.ofMinutes(6);
    }
  }
}
