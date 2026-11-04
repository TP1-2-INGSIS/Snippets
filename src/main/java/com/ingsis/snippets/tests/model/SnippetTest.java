package com.ingsis.snippets.tests.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Un test de un snippet (§4). Se acepta aunque no pase (US 8). */
@Entity
@Table(name = "snippet_test")
public class SnippetTest {

  @Id private UUID id;

  @Column(name = "snippet_id", nullable = false)
  private UUID snippetId;

  @Column(nullable = false)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<String> inputs = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "expected_outputs", nullable = false)
  private List<String> expectedOutputs = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "last_status", nullable = false)
  private TestStatus lastStatus = TestStatus.NOT_RUN;

  @Nullable
  @Column(name = "last_run_at")
  private Instant lastRunAt;

  protected SnippetTest() {
    // JPA
  }

  public SnippetTest(
      UUID id, UUID snippetId, String name, List<String> inputs, List<String> expectedOutputs) {
    this.id = id;
    this.snippetId = snippetId;
    this.name = name;
    this.inputs = inputs;
    this.expectedOutputs = expectedOutputs;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSnippetId() {
    return snippetId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getInputs() {
    return inputs;
  }

  public void setInputs(List<String> inputs) {
    this.inputs = inputs;
  }

  public List<String> getExpectedOutputs() {
    return expectedOutputs;
  }

  public void setExpectedOutputs(List<String> expectedOutputs) {
    this.expectedOutputs = expectedOutputs;
  }

  public TestStatus getLastStatus() {
    return lastStatus;
  }

  public @Nullable Instant getLastRunAt() {
    return lastRunAt;
  }

  public void recordRun(TestStatus status) {
    this.lastStatus = status;
    this.lastRunAt = Instant.now();
  }
}
