package com.universal.reconciliation.ingestion.sdk.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IngestionSdkAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IngestionSdkAutoConfiguration.class));

    @Test
    void bindsAndAppliesCustomTimeouts() {
        contextRunner
                .withPropertyValues(
                        "reconciliation.ingestion.base-url=http://localhost:9999/api",
                        "reconciliation.ingestion.username=tester",
                        "reconciliation.ingestion.password=secret",
                        "reconciliation.ingestion.connect-timeout=5s",
                        "reconciliation.ingestion.read-timeout=11s",
                        "reconciliation.ingestion.write-timeout=12s")
                .run(context -> {
                    OkHttpClient client = context.getBean(OkHttpClient.class);
                    ReconciliationIngestionProperties properties =
                            context.getBean(ReconciliationIngestionProperties.class);

                    assertThat(client.connectTimeoutMillis()).isEqualTo(5000);
                    assertThat(client.readTimeoutMillis()).isEqualTo(11000);
                    assertThat(client.writeTimeoutMillis()).isEqualTo(12000);
                    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:9999/api");
                });
    }

    @Test
    void usesDefaultTimeoutsWhenUnspecified() {
        contextRunner
                .withPropertyValues(
                        "reconciliation.ingestion.username=tester",
                        "reconciliation.ingestion.password=secret")
                .run(context -> {
                    OkHttpClient client = context.getBean(OkHttpClient.class);
                    ReconciliationIngestionProperties properties =
                            context.getBean(ReconciliationIngestionProperties.class);

                    assertThat(client.connectTimeoutMillis()).isEqualTo(30_000);
                    assertThat(client.readTimeoutMillis()).isEqualTo(60_000);
                    assertThat(client.writeTimeoutMillis()).isEqualTo(60_000);
                    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8080");
                });
    }
}
