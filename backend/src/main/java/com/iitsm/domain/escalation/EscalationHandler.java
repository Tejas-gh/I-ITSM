package com.iitsm.domain.escalation;

import java.util.Optional;

/**
 * Handler interface in the Chain of Responsibility for evaluating and escalating
 * unacknowledged P1 incidents.
 */
public interface EscalationHandler {

    /**
     * Sets the next handler in the chain.
     *
     * @param next the next handler
     */
    void setNext(EscalationHandler next);

    /**
     * Gets the next handler in the chain, or null if this is the tail.
     *
     * @return next handler
     */
    EscalationHandler getNext();

    /**
     * Evaluates the escalation context. If this handler's conditions are met and its level
     * has not yet been executed, returns an escalation decision; otherwise delegates to next.
     *
     * @param context the escalation context
     * @return decision if escalation triggers, or empty
     */
    Optional<EscalationDecision> handle(EscalationContext context);
}
