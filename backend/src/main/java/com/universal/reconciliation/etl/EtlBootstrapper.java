package com.universal.reconciliation.etl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Executes any {@link EtlPipeline} beans that are available on the classpath.
 *
 * <p>This runner now lives in the core platform so that standalone example
 * modules can contribute their own pipelines without the platform needing to
 * know about them ahead of time.</p>
 */
@Component
public class EtlBootstrapper implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EtlBootstrapper.class);

    private final List<EtlPipeline> pipelines;

    public EtlBootstrapper(List<EtlPipeline> pipelines) {
        this.pipelines = pipelines;
    }

    @Override
    public void run(String... args) {
        if (pipelines.isEmpty()) {
            log.info("No ETL pipelines registered – running platform without seeded examples");
            return;
        }
        for (EtlPipeline pipeline : pipelines) {
            log.info("Executing ETL pipeline: {}", pipeline.name());
            pipeline.run();
        }
    }
}
