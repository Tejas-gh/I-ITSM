package com.iitsm.domain.model;

/** Business criticality of an IT service on a 1-4 scale. */
public enum BusinessCriticality {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    BusinessCriticality(int weight) {
        this.weight = weight;
    }

    /** Numeric weight (1-4) used in the priority calculation. */
    public int weight() {
        return weight;
    }
}
