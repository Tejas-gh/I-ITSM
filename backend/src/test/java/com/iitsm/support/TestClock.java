package com.iitsm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Controllable Clock for testing time-dependent logic.
 */
public final class TestClock extends Clock {

    public static final Instant DEFAULT_START = Instant.parse("2026-11-16T09:00:00Z");

    private Instant current;
    private final ZoneId zone;

    public TestClock() {
        this(DEFAULT_START);
    }

    public TestClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private TestClock(Instant start, ZoneId zone) {
        this.current = (start != null) ? start : DEFAULT_START;
        this.zone = (zone != null) ? zone : ZoneOffset.UTC;
    }

    public void advance(Duration d) {
        if (d != null) {
            this.current = this.current.plus(d);
        }
    }

    public void set(Instant t) {
        if (t != null) {
            this.current = t;
        }
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId z) {
        return new TestClock(this.current, z);
    }
}

