package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.factory.IncidentIdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe sequence generator producing incident IDs in the format "INC1001", "INC1002", etc.
 */
public final class InMemoryIncidentIdGenerator implements IncidentIdGenerator {

    private static final long DEFAULT_FIRST_NUMBER = 1001L;

    private final AtomicLong counter;

    public InMemoryIncidentIdGenerator() {
        this(DEFAULT_FIRST_NUMBER);
    }

    public InMemoryIncidentIdGenerator(long firstNumber) {
        if (firstNumber < 1) {
            throw new InvalidInputException("firstNumber must be >= 1");
        }
        this.counter = new AtomicLong(firstNumber);
    }

    @Override
    public String nextIncidentId() {
        return "INC" + counter.getAndIncrement();
    }
}

