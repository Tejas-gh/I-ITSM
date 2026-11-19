package com.iitsm.domain.escalation;

import java.util.Optional;

/**
 * Abstract base class providing common chaining behavior for escalation handlers.
 */
public abstract class AbstractEscalationHandler implements EscalationHandler {

    private EscalationHandler next;

    @Override
    public void setNext(EscalationHandler next) {
        this.next = next;
    }

    @Override
    public EscalationHandler getNext() {
        return next;
    }

    /**
     * Passes the context to the next handler in the chain, or returns empty if at tail.
     *
     * @param context the escalation context
     * @return decision from downstream handler or empty
     */
    protected Optional<EscalationDecision> passToNext(EscalationContext context) {
        if (next != null) {
            return next.handle(context);
        }
        return Optional.empty();
    }
}
