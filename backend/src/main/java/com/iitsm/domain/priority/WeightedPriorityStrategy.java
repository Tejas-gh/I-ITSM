package com.iitsm.domain.priority;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;

public final class WeightedPriorityStrategy implements PriorityStrategy {

    private static final int BUMP_NONE = 0;
    private static final int BUMP_MEDIUM = 4;
    private static final int BUMP_LARGE = 8;
    private static final int BUMP_HUGE = 12;

    private static final int USERS_MEDIUM_FROM = 10;
    private static final int USERS_LARGE_FROM = 50;
    private static final int USERS_HUGE_FROM = 200;

    private static final int P1_MIN_SCORE = 36;
    private static final int P2_MIN_SCORE = 18;
    private static final int P3_MIN_SCORE = 8;

    @Override
    public PriorityResult calculate(PriorityInput input) {
        if (input == null) {
            throw new InvalidInputException("input must not be null");
        }
        int base = input.impact().weight()
                * input.urgency().weight()
                * input.businessCriticality().weight();
        int score = base + bump(input.affectedUsers());
        return new PriorityResult(score, levelFor(score));
    }

    private static int bump(int affectedUsers) {
        if (affectedUsers >= USERS_HUGE_FROM) {
            return BUMP_HUGE;
        }
        if (affectedUsers >= USERS_LARGE_FROM) {
            return BUMP_LARGE;
        }
        if (affectedUsers >= USERS_MEDIUM_FROM) {
            return BUMP_MEDIUM;
        }
        return BUMP_NONE;
    }

    private static PriorityLevel levelFor(int score) {
        if (score >= P1_MIN_SCORE) {
            return PriorityLevel.P1;
        }
        if (score >= P2_MIN_SCORE) {
            return PriorityLevel.P2;
        }
        if (score >= P3_MIN_SCORE) {
            return PriorityLevel.P3;
        }
        return PriorityLevel.P4;
    }
}
