package com.ingsis.snippets.download;

import com.ingsis.snippets.common.identity.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Descarga de un snippet (US 13). */
@RestController
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DownloadProperties.class)
class DownloadController {

  private final DownloadService service;

  DownloadController(DownloadService service) {
    this.service = service;
  }

  @GetMapping("/snippets/{id}/download")
  ResponseEntity<byte[]> download(
      CurrentUser user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean formatted) {
    DownloadContent result = service.download(user, id, formatted);
    byte[] body = result.content().getBytes(StandardCharsets.UTF_8);
    ContentDisposition disposition =
        ContentDisposition.attachment().filename(result.filename()).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
        .body(body);
  }
}
