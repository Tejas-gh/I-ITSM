package com.iitsm.domain.factory;

public interface IncidentIdGenerator {

    String nextIncidentId();      // "INC1001", "INC1002", ... never repeats
}
