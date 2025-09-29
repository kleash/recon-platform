package com.universal.reconciliation.examples.ingestionsdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IngestionSdkExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionSdkExampleApplication.class, args);
    }
}
