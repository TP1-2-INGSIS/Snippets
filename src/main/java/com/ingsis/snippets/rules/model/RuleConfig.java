package com.ingsis.snippets.rules.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * Reglas de un usuario para un lenguaje y tipo (§4, D07). {@code config} es JSON opaco: este
 * servicio no lo interpreta, solo lo reenvía a Language Service.
 */
@Entity
@Table(name = "rule_config")
@IdClass(RuleConfigId.class)
public class RuleConfig {

  @Id
  @Column(name = "user_id")
  private String userId;

  @Id private String language;

  @Id
  @Enumerated(EnumType.STRING)
  private RuleKind kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private JsonNode config;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RuleConfig() {
    // JPA
  }

  public RuleConfig(String userId, String language, RuleKind kind, JsonNode config) {
    this.userId = userId;
    this.language = language;
    this.kind = kind;
    this.config = config;
  }

  public String getUserId() {
    return userId;
  }

  public String getLanguage() {
    return language;
  }

  public RuleKind getKind() {
    return kind;
  }

  public JsonNode getConfig() {
    return config;
  }

  public void setConfig(JsonNode config) {
    this.config = config;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now();
  }
}
