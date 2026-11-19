package com.iitsm.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.exception.InvalidInputException;

/**
 * Thread-safe in-memory implementation of {@link EscalationRepository}.
 *
 * <p>Uses synchronized data structures with an {@link AtomicLong} id sequence.
 * Serves as the test and default in-memory adapter for Gate B6 until Developer A's
 * Gate A7 persistent JPA adapter is wired.</p>
 */
public final class InMemoryEscalationRepository implements EscalationRepository {

    private final AtomicLong idSequence = new AtomicLong(1);
    private final Map<Long, EscalationRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized EscalationRecord save(EscalationRecord record) {
        if (record == null) {
            throw new InvalidInputException("record must not be null");
        }

        Long id = record.id();
        if (id == null) {
            id = idSequence.getAndIncrement();
        }

        EscalationRecord saved = new EscalationRecord(
                id,
                record.incidentId(),
                record.level(),
                record.targetRole(),
                record.escalatedAt(),
                record.reason()
        );

        records.put(id, saved);
        return saved;
    }

    @Override
    public synchronized List<EscalationRecord> findByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return Collections.emptyList();
        }

        List<EscalationRecord> list = new ArrayList<>();
        for (EscalationRecord r : records.values()) {
            if (incidentId.equals(r.incidentId())) {
                list.add(r);
            }
        }
        list.sort(Comparator.comparing(EscalationRecord::escalatedAt));
        return Collections.unmodifiableList(list);
    }

    @Override
    public synchronized Optional<EscalationRecord> findLatestByIncidentId(String incidentId) {
        List<EscalationRecord> list = findByIncidentId(incidentId);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }

    @Override
    public synchronized boolean existsByIncidentIdAndLevel(String incidentId, int level) {
        if (incidentId == null || incidentId.isBlank()) {
            return false;
        }

        for (EscalationRecord r : records.values()) {
            if (incidentId.equals(r.incidentId()) && r.level() == level) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized List<EscalationRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    public synchronized void clear() {
        records.clear();
        idSequence.set(1);
    }
}
