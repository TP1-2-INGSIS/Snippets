package com.ingsis.snippets.snippet.repository;

import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * El listado parte de los ids que da Permission Service y filtra en la base (D09): una sola
 * consulta con {@code id IN (…)} + filtros opcionales + orden + paginación.
 */
public interface SnippetRepository extends JpaRepository<Snippet, UUID> {

  /**
   * @param ids los snippets visibles para el usuario (de Permission Service)
   * @param namePattern filtro "contiene" ya en minúsculas y entre {@code %} (ej. {@code "%hola%"});
   *     {@code null} = sin filtro
   * @param language id exacto; {@code null} = sin filtro
   * @param lintStatus exacto; {@code null} = sin filtro
   */
  @Query(
      """
      SELECT s FROM Snippet s
      WHERE s.id IN :ids
      AND (CAST(:namePattern AS string) IS NULL OR LOWER(s.name) LIKE CAST(:namePattern AS string))
      AND (CAST(:language AS string) IS NULL OR s.language = :language)
      AND (:lintStatus IS NULL OR s.lintStatus = :lintStatus)
      """)
  Page<Snippet> search(
      @Param("ids") Collection<UUID> ids,
      @Param("namePattern") @Nullable String namePattern,
      @Param("language") @Nullable String language,
      @Param("lintStatus") @Nullable LintStatus lintStatus,
      Pageable pageable);

  /**
   * Variante que toma el nombre crudo y arma el patrón "contiene" en minúsculas.
   *
   * @param name texto a buscar dentro de {@code name}; {@code null} o en blanco = sin filtro
   */
  default Page<Snippet> searchByName(
      Collection<UUID> ids,
      @Nullable String name,
      @Nullable String language,
      @Nullable LintStatus lintStatus,
      Pageable pageable) {
    String pattern = (name == null || name.isBlank()) ? null : "%" + name.toLowerCase() + "%";
    return search(ids, pattern, language, lintStatus, pageable);
  }

  /**
   * Lints pendientes desde antes de {@code cutoff}, o sin fecha de pedido (candidatos del reaper).
   * {@code SKIP LOCKED}: dos instancias no re-encolan el mismo snippet a la vez.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      SELECT s FROM Snippet s
      WHERE s.lintStatus = :status
      AND (s.lintRequestedAt IS NULL OR s.lintRequestedAt < :cutoff)
      """)
  List<Snippet> findStalledLint(
      @Param("status") LintStatus status, @Param("cutoff") Instant cutoff);

  /** Igual que {@link #findStalledLint} para los formateos pendientes. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      SELECT s FROM Snippet s
      WHERE s.formatStatus = :status
      AND (s.formatRequestedAt IS NULL OR s.formatRequestedAt < :cutoff)
      """)
  List<Snippet> findStalledFormat(
      @Param("status") FormatStatus status, @Param("cutoff") Instant cutoff);
}
