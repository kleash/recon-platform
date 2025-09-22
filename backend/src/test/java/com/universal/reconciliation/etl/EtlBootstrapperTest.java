package com.universal.reconciliation.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EtlBootstrapperTest {

    @Test
    void run_executesEachPipeline() {
        AtomicInteger counter = new AtomicInteger();
        EtlPipeline first = new TrackingPipeline("First", counter);
        EtlPipeline second = new TrackingPipeline("Second", counter);

        new EtlBootstrapper(List.of(first, second)).run();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void run_skipsExecutionWhenNoPipelinesRegistered() {
        EtlBootstrapper bootstrapper = new EtlBootstrapper(List.of());

        assertThatCode(bootstrapper::run).doesNotThrowAnyException();
    }

    private static final class TrackingPipeline implements EtlPipeline {

        private final String name;
        private final AtomicInteger counter;

        TrackingPipeline(String name, AtomicInteger counter) {
            this.name = name;
            this.counter = counter;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void run() {
            counter.incrementAndGet();
        }
    }
}
