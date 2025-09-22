package com.universal.reconciliation.etl;

/**
 * Contract implemented by each sample ETL pipeline.
 */
public interface EtlPipeline {

    /**
     * Human readable name used for diagnostics.
     */
    String name();

    /**
     * Executes the pipeline end-to-end.
     */
    void run();
}
