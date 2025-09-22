package com.universal.reconciliation.examples.custodian;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Controllable clock so the ETL pipeline can deterministically advance time during tests.
 */
@Component
public class ScenarioClock extends Clock {

    private final ZoneId zone;
    private Instant current;

    public ScenarioClock() {
        this.zone = ZoneId.of("America/New_York");
        this.current = LocalDate.of(2024, 3, 18).atStartOfDay(zone).toInstant();
    }

    private ScenarioClock(ZoneId zone, Instant current) {
        this.zone = zone;
        this.current = current;
    }

    public void set(LocalDate date, LocalTime time) {
        this.current = LocalDateTime.of(date, time).atZone(zone).toInstant();
    }

    public void advanceTo(LocalDateTime dateTime) {
        this.current = dateTime.atZone(zone).toInstant();
    }

    public void advanceTo(LocalDate date, LocalTime time) {
        set(date, time);
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ScenarioClock(zone, current);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
