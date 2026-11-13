package com.iitsm.infrastructure.persistence.jpa;

import org.springframework.stereotype.Component;

import com.iitsm.domain.factory.IncidentIdGenerator;

import jakarta.persistence.EntityManager;

/**
 * Database-backed {@link IncidentIdGenerator}: ids come from the {@code incident_id_seq} sequence (V5), so they
 * never repeat across restarts or instances. Format: {@code INC1001}, {@code INC1002}, ...
 */
@Component
public class JpaIncidentIdGenerator implements IncidentIdGenerator {

    private final EntityManager entityManager;

    public JpaIncidentIdGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String nextIncidentId() {
        Number next = (Number) entityManager.createNativeQuery("select nextval('incident_id_seq')").getSingleResult();
        return "INC" + next.longValue();
    }
}
