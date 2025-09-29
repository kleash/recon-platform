package com.universal.reconciliation.ingestion.sdk.autoconfig;

import com.universal.reconciliation.ingestion.sdk.IngestionPipeline;
import com.universal.reconciliation.ingestion.sdk.ReconciliationIngestionClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the core ingestion SDK beans when the library is present on the classpath.
 */
@AutoConfiguration
@EnableConfigurationProperties(ReconciliationIngestionProperties.class)
@ConditionalOnProperty(prefix = "reconciliation.ingestion", name = {"username", "password"})
public class IngestionSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient ingestionOkHttpClient() {
        return new OkHttpClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReconciliationIngestionClient reconciliationIngestionClient(
            OkHttpClient client,
            ReconciliationIngestionProperties properties) {
        return new ReconciliationIngestionClient(client, properties.getBaseUrl(), properties.getUsername(), properties.getPassword());
    }

    @Bean
    @ConditionalOnMissingBean
    public IngestionPipeline ingestionPipeline(ReconciliationIngestionClient client) {
        return new IngestionPipeline(client);
    }
}
