package com.ingsis.snippets.snippet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Un snippet (§4). {@code content} se accede solo vía {@link SnippetStorage} (D06), pero vive en
 * esta misma fila por ahora. {@code ownerId} es desnormalizado para mostrar y ordenar: NO se usa
 * para autorizar (D05). {@code lintStatus} se persiste (D08).
 */
@Entity
@Table(name = "snippet")
public class Snippet {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Nullable @Column private String description;

  @Column(nullable = false)
  private String language;

  @Column(nullable = false)
  private String version;

  @Column(nullable = false)
  private String content;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "lint_status", nullable = false)
  private LintStatus lintStatus = LintStatus.PENDING;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "lint_violations", nullable = false)
  private List<SourceViolation> lintViolations = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Snippet() {
    // JPA
  }

  public Snippet(
      UUID id,
      String name,
      @Nullable String description,
      String language,
      String version,
      String content,
      String ownerId) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.language = language;
    this.version = version;
    this.content = content;
    this.ownerId = ownerId;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public LintStatus getLintStatus() {
    return lintStatus;
  }

  public void setLintStatus(LintStatus lintStatus) {
    this.lintStatus = lintStatus;
  }

  public List<SourceViolation> getLintViolations() {
    return lintViolations;
  }

  public void setLintViolations(List<SourceViolation> lintViolations) {
    this.lintViolations = lintViolations;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
