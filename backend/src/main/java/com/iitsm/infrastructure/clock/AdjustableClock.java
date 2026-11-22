package com.iitsm.infrastructure.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class AdjustableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Duration> offset;

    public AdjustableClock(ZoneId zone) {
        this(zone, new AtomicReference<>(Duration.ZERO));
    }

    private AdjustableClock(ZoneId zone, AtomicReference<Duration> offset) {
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
        this.offset = Objects.requireNonNull(offset, "offset must not be null");
    }

    public void advance(Duration duration) {
        if (duration != null) {
            offset.updateAndGet(d -> d.plus(duration));
        }
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration getOffset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(zone, this.offset);
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset.get());
    }
}
