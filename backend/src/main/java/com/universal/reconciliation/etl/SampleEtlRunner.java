package com.universal.reconciliation.etl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the execution of all registered ETL pipelines at startup.
 */
@Component
public class SampleEtlRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleEtlRunner.class);

    private final List<EtlPipeline> pipelines;

    public SampleEtlRunner(List<EtlPipeline> pipelines) {
        this.pipelines = pipelines;
    }

    @Override
    public void run(String... args) {
        for (EtlPipeline pipeline : pipelines) {
            log.info("Executing sample ETL pipeline: {}", pipeline.name());
            pipeline.run();
        }
    }
}
