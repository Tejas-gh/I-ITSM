package com.iitsm.application.listener;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.exception.InvalidInputException;

import java.util.Objects;

/**
 * Synchronous domain event listener that records audit trails for all domain events.
 * Has {@code critical() = true} so any audit persistence failures propagate.
 */
public final class AuditEventListener implements DomainEventListener {

    private final AuditRepository auditRepository;

    public AuditEventListener(AuditRepository auditRepository) {
        if (auditRepository == null) {
            throw new InvalidInputException("auditRepository must not be null");
        }
        this.auditRepository = auditRepository;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event == null) {
            throw new InvalidInputException("event must not be null");
        }
        AuditEntry entry = AuditEntry.from(event);
        auditRepository.save(entry);
    }

    @Override
    public boolean critical() {
        return true;
    }
}
