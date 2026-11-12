package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.incident.IncidentNote;
import com.iitsm.domain.model.NoteType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code incident_notes}; the identity id preserves note order. */
@Entity
@Table(name = "incident_notes")
public class IncidentNoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String incidentId;
    String authorId;
    @Enumerated(EnumType.STRING)
    NoteType noteType;
    String noteText;
    Instant createdAt;

    protected IncidentNoteEntity() {
    }

    static IncidentNoteEntity from(String incidentId, IncidentNote note) {
        IncidentNoteEntity e = new IncidentNoteEntity();
        e.incidentId = incidentId;
        e.authorId = note.authorId();
        e.noteType = note.type();
        e.noteText = note.text();
        e.createdAt = note.createdAt();
        return e;
    }

    IncidentNote toDomain() {
        return new IncidentNote(authorId, noteType, noteText, createdAt);
    }
}
