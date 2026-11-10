package com.iitsm.domain.classification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoOpClassifierTest {

    private final CategoryClassifier classifier = new NoOpClassifier();

    @Test
    void classify_anyInputIncludingBlank_returnsEmpty() {
        assertFalse(classifier.classify("VPN is down", "Cannot connect to the network").isPresent());
        assertFalse(classifier.classify("", "").isPresent());
        assertFalse(classifier.classify("   ", null).isPresent());
        assertFalse(classifier.classify(null, null).isPresent());
    }
}
