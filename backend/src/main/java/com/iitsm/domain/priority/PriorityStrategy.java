package com.iitsm.domain.priority;

public interface PriorityStrategy {

    PriorityResult calculate(PriorityInput input);        // throws InvalidInputException if input == null
}
