package com.ingsis.snippets.snippet.storage;

import com.ingsis.snippets.clients.ClientsConfiguration;
import com.ingsis.snippets.clients.ServicesProperties;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

/** Cablea el modo {@code assets} (SNIP-4, D3): storage + migrador de filas viejas. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "snippets.storage", havingValue = "assets")
class StorageConfiguration {

  @Bean
  AssetSnippetStorage assetSnippetStorage(
      RestClient.Builder builder,
      ServicesProperties properties,
      SnippetRepository snippets,
      PlatformTransactionManager transactions) {
    RestClient client = ClientsConfiguration.restClient(builder, properties.assets(), properties);
    return new AssetSnippetStorage(client, snippets, transactions);
  }

  @Bean
  SnippetContentMigrator snippetContentMigrator(
      AssetSnippetStorage storage,
      SnippetRepository snippets,
      @Value("${snippets.migration.batch-size:100}") int batchSize) {
    return new SnippetContentMigrator(storage, snippets, batchSize);
  }

  @Bean
  SnippetMigrationRunner snippetMigrationRunner(
      SnippetContentMigrator migrator,
      @Value("${snippets.migration.on-startup:true}") boolean onStartup) {
    return new SnippetMigrationRunner(migrator, onStartup);
  }
}
