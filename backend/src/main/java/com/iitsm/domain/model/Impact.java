package com.iitsm.domain.model;

/** Business impact of an incident on a 1-4 scale. */
public enum Impact {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Impact(int weight) {
        this.weight = weight;
    }

    /** Numeric weight (1-4) used in the priority calculation. */
    public int weight() {
        return weight;
    }
}
