package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentNote;
import com.iitsm.domain.incident.Resolution;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.IncidentSearchCriteria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * JPA implementation of the domain {@link IncidentRepository}.
 *
 * <p>Domain to database: {@link IncidentEntity#from(Incident)} plus append-only notes.
 * Database to domain: relations are resolved to domain objects and the aggregate is rebuilt through
 * {@link Incident#restore}. Lists are ordered by (createdAt, id); the A3 schema has no insertion-sequence
 * column, and incident ids and creation times are generated monotonically.
 */
@Repository
@Transactional
public class JpaIncidentRepository implements IncidentRepository {

    private static final List<IncidentStatus> WORKLOAD_STATUSES = Arrays.stream(IncidentStatus.values())
            .filter(IncidentStatus::countsTowardWorkload)
            .toList();

    private final IncidentEntityRepository incidents;
    private final IncidentNoteEntityRepository notes;
    private final UserEntityRepository users;
    private final ItServiceEntityRepository services;
    private final CategoryEntityRepository categories;
    private final EntityManager entityManager;

    public JpaIncidentRepository(IncidentEntityRepository incidents, IncidentNoteEntityRepository notes,
                                 UserEntityRepository users, ItServiceEntityRepository services,
                                 CategoryEntityRepository categories, EntityManager entityManager) {
        this.incidents = incidents;
        this.notes = notes;
        this.users = users;
        this.services = services;
        this.categories = categories;
        this.entityManager = entityManager;
    }

    @Override
    public Incident save(Incident incident) {
        if (incident == null) {
            throw new InvalidInputException("incident must not be null");
        }
        // Flush so the incident row exists before note rows (FK) are inserted with IDENTITY ids.
        incidents.saveAndFlush(IncidentEntity.from(incident));

        // Notes are append-only in the domain: insert only the ones not stored yet.
        String id = incident.getId();
        List<IncidentNote> domainNotes = incident.getNotes();
        long stored = notes.countByIncidentId(id);
        if (domainNotes.size() < stored) {
            notes.deleteByIncidentId(id);
            notes.flush();
            stored = 0;
        }
        for (int i = (int) stored; i < domainNotes.size(); i++) {
            notes.save(IncidentNoteEntity.from(id, domainNotes.get(i)));
        }
        return incident;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Incident> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return incidents.findById(id).map(e -> toDomain(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> findAll() {
        return toDomain(incidents.findAllByOrderByCreatedAtAscIdAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> findByReporterId(String reporterId) {
        if (reporterId == null) {
            throw new InvalidInputException("reporterId must not be null");
        }
        return toDomain(incidents.findByReporterIdOrderByCreatedAtAscIdAsc(reporterId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> findByAssignedTechnicianId(String technicianId) {
        if (technicianId == null) {
            throw new InvalidInputException("technicianId must not be null");
        }
        return toDomain(incidents.findByAssignedTechnicianIdOrderByCreatedAtAscIdAsc(technicianId));
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveByTechnicianId(String technicianId) {
        if (technicianId == null) {
            throw new InvalidInputException("technicianId must not be null");
        }
        return (int) incidents.countByAssignedTechnicianIdAndStatusIn(technicianId, WORKLOAD_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> search(IncidentSearchCriteria criteria) {
        if (criteria == null) {
            throw new InvalidInputException("criteria must not be null");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<IncidentEntity> query = cb.createQuery(IncidentEntity.class);
        Root<IncidentEntity> root = query.from(IncidentEntity.class);

        List<Predicate> where = new ArrayList<>();
        if (criteria.incidentId() != null) {
            where.add(cb.equal(root.get("id"), criteria.incidentId()));
        }
        if (criteria.createdFrom() != null) {
            where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            where.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdTo()));
        }
        if (criteria.status() != null) {
            where.add(cb.equal(root.get("status"), criteria.status()));
        }
        if (criteria.priority() != null) {
            where.add(cb.equal(root.get("priority"), criteria.priority()));
        }
        if (criteria.categoryId() != null) {
            where.add(cb.equal(root.get("categoryId"), criteria.categoryId()));
        }
        if (criteria.serviceId() != null) {
            where.add(cb.equal(root.get("serviceId"), criteria.serviceId()));
        }
        if (criteria.technicianId() != null) {
            where.add(cb.equal(root.get("assignedTechnicianId"), criteria.technicianId()));
        }
        query.select(root)
                .where(where.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("createdAt")), cb.asc(root.get("id")));
        return toDomain(entityManager.createQuery(query).getResultList());
    }

    // ---- entity -> domain ----

    private List<Incident> toDomain(List<IncidentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<String> ids = entities.stream().map(e -> e.id).toList();
        Map<String, List<IncidentNote>> notesByIncident = new HashMap<>();
        for (IncidentNoteEntity note : notes.findByIncidentIdInOrderByIdAsc(ids)) {
            notesByIncident.computeIfAbsent(note.incidentId, k -> new ArrayList<>()).add(note.toDomain());
        }
        Map<String, User> userCache = new HashMap<>();
        Map<String, ItService> serviceCache = new HashMap<>();
        Map<String, Category> categoryCache = new HashMap<>();

        List<Incident> result = new ArrayList<>(entities.size());
        for (IncidentEntity e : entities) {
            User reporter = user(e.reporterId, userCache);
            Technician technician = null;
            if (e.assignedTechnicianId != null) {
                User assigned = user(e.assignedTechnicianId, userCache);
                if (!(assigned instanceof Technician t)) {
                    throw new IllegalStateException("Assigned user " + e.assignedTechnicianId
                            + " of incident " + e.id + " is not a technician");
                }
                technician = t;
            }
            Resolution resolution = e.resolutionResolverId == null ? null
                    : new Resolution(e.resolutionResolverId, e.resolutionSummary, e.resolutionResolvedAt);
            Feedback feedback = e.feedbackAuthorId == null ? null
                    : new Feedback(e.feedbackAuthorId, e.feedbackRating, e.feedbackComment, e.feedbackSubmittedAt);

            result.add(Incident.restore(
                    e.id, e.title, e.description, reporter, service(e.serviceId, serviceCache),
                    category(e.categoryId, categoryCache),
                    e.suggestedCategoryId == null ? null : category(e.suggestedCategoryId, categoryCache),
                    e.categorySource, e.impact, e.urgency, e.affectedUsers, e.priority, e.priorityScore,
                    e.createdAt, e.status, technician, e.assignedAt, e.acknowledgedAt, e.verifiedAt,
                    e.closedAt, e.closedById, e.closureNote, e.responseDeadline, e.resolutionDeadline,
                    e.waitingSince, java.time.Duration.ofNanos(e.pausedDurationNanos),
                    notesByIncident.getOrDefault(e.id, List.of()), resolution, feedback));
        }
        return List.copyOf(result);
    }

    private User user(String id, Map<String, User> cache) {
        return cache.computeIfAbsent(id, key -> users.findById(key).map(UserEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Missing user " + key)));
    }

    private ItService service(String id, Map<String, ItService> cache) {
        return cache.computeIfAbsent(id, key -> services.findById(key).map(ItServiceEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Missing service " + key)));
    }

    private Category category(String id, Map<String, Category> cache) {
        return cache.computeIfAbsent(id, key -> categories.findById(key).map(CategoryEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Missing category " + key)));
    }
}
