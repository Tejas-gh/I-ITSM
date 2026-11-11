package com.iitsm.application.service;

import com.iitsm.application.command.AssignIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.event.AssignmentFailed;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.event.IncidentStatusChanged;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.exception.TechnicianOverloadedException;
import com.iitsm.domain.exception.TechnicianUnavailableException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceAssignmentTest {

    private ServiceFixture f;
    private Incident unassignedIncident;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();

        // Create incident with all techs temporarily deactivated so it remains CLASSIFIED
        f.technician.deactivate();
        f.technician2.deactivate();

        unassignedIncident = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "System Bug", "Details", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.MEDIUM, Urgency.MEDIUM, 1
        ));

        // Reactivate technicians for assignment tests
        f.technician.activate();
        f.technician2.activate();
        f.listener.clear();
    }

    @Test
    @DisplayName("manual assign succeeds and publishes IncidentAssigned event")
    void assignIncident_validAdmin_assignsTechnicianAndPublishesEvent() {
        AssignIncidentCommand cmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId());

        Incident updated = f.service.assignIncident(cmd);

        assertEquals(IncidentStatus.ASSIGNED, updated.getStatus());
        assertTrue(updated.getAssignedTechnician().isPresent());
        assertEquals(f.technician.getId(), updated.getAssignedTechnician().get().getId());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentAssigned.class, events.get(0));
        IncidentAssigned event = (IncidentAssigned) events.get(0);
        assertFalse(event.automatic());
        assertEquals(f.admin.getId(), event.actorId());
        assertEquals(f.technician.getId(), event.technicianId());
    }

    @Test
    @DisplayName("assign to unavailable technician throws TechnicianUnavailableException")
    void assignIncident_unavailableTechnician_throwsTechnicianUnavailableException() {
        f.technician.markUnavailable();
        AssignIncidentCommand cmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId());

        assertThrows(TechnicianUnavailableException.class, () -> f.service.assignIncident(cmd));
    }

    @Test
    @DisplayName("assign to overloaded technician throws TechnicianOverloadedException")
    void assignIncident_overloadedTechnician_throwsTechnicianOverloadedException() {
        // technician has maxActive = 5, assign first incident to him
        f.service.assignIncident(new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId()));

        // Create 4 more incidents without auto-assigning (both technicians deactivated during create)
        for (int i = 0; i < 4; i++) {
            f.technician.deactivate();
            f.technician2.deactivate();
            Incident inc = f.service.createIncident(new CreateIncidentCommand(
                    f.employee.getId(), "Bug " + i, "Desc", f.itService.getId(),
                    f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
            ));
            f.technician.activate();
            f.technician2.activate();
            f.service.assignIncident(new AssignIncidentCommand(f.admin.getId(), inc.getId(), f.technician.getId()));
        }

        assertEquals(5, f.incidents.countActiveByTechnicianId(f.technician.getId()));

        // Create a 6th unassigned incident and try to assign to him
        f.technician.deactivate();
        f.technician2.deactivate();
        Incident inc6 = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Bug 6", "Desc", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        ));
        f.technician.activate();
        f.technician2.activate();

        AssignIncidentCommand overloadCmd = new AssignIncidentCommand(f.admin.getId(), inc6.getId(), f.technician.getId());
        assertThrows(TechnicianOverloadedException.class, () -> f.service.assignIncident(overloadCmd));
    }

    @Test
    @DisplayName("assign to user who is not a technician throws InvalidInputException")
    void assignIncident_targetNotTechnician_throwsInvalidInputException() {
        AssignIncidentCommand cmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.employee.getId());

        assertThrows(InvalidInputException.class, () -> f.service.assignIncident(cmd));
    }

    @Test
    @DisplayName("assign in invalid state throws InvalidStateTransitionException")
    void assignIncident_wrongState_throwsInvalidStateTransitionException() {
        // First assign
        f.service.assignIncident(new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId()));

        // Try to assign again from ASSIGNED state (should use reassign, not assign)
        AssignIncidentCommand cmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician2.getId());
        assertThrows(InvalidStateTransitionException.class, () -> f.service.assignIncident(cmd));
    }

    @Test
    @DisplayName("non-admin attempting assign is rejected with UnauthorizedOperationException")
    void assignIncident_nonAdminActor_throwsUnauthorizedOperationException() {
        AssignIncidentCommand cmd = new AssignIncidentCommand(f.employee.getId(), unassignedIncident.getId(), f.technician.getId());

        assertThrows(UnauthorizedOperationException.class, () -> f.service.assignIncident(cmd));
    }

    @Test
    @DisplayName("reassign unassigns and reassigns publishing both UNASSIGNED and ASSIGNED events")
    void reassignIncident_validAdmin_unassignsAndReassignsWithEvents() {
        f.service.assignIncident(new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId()));
        f.listener.clear();

        AssignIncidentCommand reassignCmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician2.getId());
        Incident reassigned = f.service.reassignIncident(reassignCmd);

        assertEquals(f.technician2.getId(), reassigned.getAssignedTechnician().get().getId());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(2, events.size());
        assertInstanceOf(IncidentStatusChanged.class, events.get(0));
        assertEquals("UNASSIGNED", events.get(0).action());
        assertInstanceOf(IncidentAssigned.class, events.get(1));
    }

    @Test
    @DisplayName("reassign to the same technician throws InvalidInputException")
    void reassignIncident_sameTechnician_throwsInvalidInputException() {
        f.service.assignIncident(new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId()));

        AssignIncidentCommand sameTechCmd = new AssignIncidentCommand(f.admin.getId(), unassignedIncident.getId(), f.technician.getId());
        assertThrows(InvalidInputException.class, () -> f.service.reassignIncident(sameTechCmd));
    }

    @Test
    @DisplayName("autoAssignIncident by admin picks candidate via strategy and publishes automatic IncidentAssigned")
    void autoAssignIncident_adminActor_assignsViaStrategy() {
        f.assignmentStrategy.setDesignatedTechnician(f.technician2);

        Incident assigned = f.service.autoAssignIncident(f.admin.getId(), unassignedIncident.getId());

        assertEquals(f.technician2.getId(), assigned.getAssignedTechnician().get().getId());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentAssigned.class, events.get(0));
        IncidentAssigned event = (IncidentAssigned) events.get(0);
        assertTrue(event.automatic());
        assertEquals(f.admin.getId(), event.actorId());
    }
}
