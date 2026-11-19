package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.api.dto.CheckDuplicateRequest;
import com.iitsm.api.dto.CheckDuplicateResponse;
import com.iitsm.api.dto.DuplicateCandidateResponse;
import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Urgency;

class DuplicateDetectionServiceTest {

    private ServiceFixture f;
    private DuplicateDetectionService duplicateService;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        duplicateService = new DuplicateDetectionService(f.incidents);
    }

    @Test
    @DisplayName("null or blank title returns hasDuplicates=false and empty candidates")
    void nullOrBlankTitle_returnsEmpty() {
        CheckDuplicateResponse res1 = duplicateService.checkDuplicates(null);
        assertFalse(res1.hasDuplicates());
        assertTrue(res1.candidates().isEmpty());

        CheckDuplicateResponse res2 = duplicateService.checkDuplicates(new CheckDuplicateRequest("", "desc", null, null));
        assertFalse(res2.hasDuplicates());
        assertTrue(res2.candidates().isEmpty());

        CheckDuplicateResponse res3 = duplicateService.checkDuplicates(new CheckDuplicateRequest("   \t  ", "desc", null, null));
        assertFalse(res3.hasDuplicates());
        assertTrue(res3.candidates().isEmpty());
    }

    @Test
    @DisplayName("exact title match returns candidate with score 1.0")
    void exactTitleMatch_returnsScoreOne() {
        Incident inc = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(),
                "VPN connection drops frequently",
                "Cannot connect to internal network",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                5
        ));

        CheckDuplicateResponse res = duplicateService.checkDuplicates(new CheckDuplicateRequest(
                "vpn connection drops frequently",
                "anything",
                f.itService.getId(),
                f.category.getId()
        ));

        assertTrue(res.hasDuplicates());
        assertEquals(1, res.candidates().size());
        DuplicateCandidateResponse candidate = res.candidates().get(0);
        assertEquals(inc.getId(), candidate.id());
        assertEquals("VPN connection drops frequently", candidate.title());
        assertEquals(1.0, candidate.score());
        assertTrue(candidate.matchReason().contains("Exact title match"));
    }

    @Test
    @DisplayName("token overlap with matching service and category returns boosted similarity")
    void tokenOverlap_withServiceCategoryBonus() {
        Incident inc = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(),
                "Database connection failure in payment gateway",
                "DB connection pool exhausted",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                50
        ));

        CheckDuplicateResponse res = duplicateService.checkDuplicates(new CheckDuplicateRequest(
                "Payment gateway database connection timeout",
                "New incident description",
                f.itService.getId(),
                f.category.getId()
        ));

        assertTrue(res.hasDuplicates());
        assertFalse(res.candidates().isEmpty());
        DuplicateCandidateResponse top = res.candidates().get(0);
        assertEquals(inc.getId(), top.id());
        assertTrue(top.score() > 0.5);
        assertTrue(top.matchReason().contains("within same service and category"));
        assertTrue(top.matchReason().contains("Shares keyword(s)"));
    }

    @Test
    @DisplayName("closed incidents are ignored during duplicate scan")
    void closedIncidents_areIgnored() {
        Incident inc = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(),
                "Printer offline on 3rd floor",
                "Printer power light is off",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.LOW,
                Urgency.LOW,
                2
        ));

        // Advance lifecycle to closed
        Instant now = f.clock.instant();
        inc.acknowledge(now);
        inc.startWork();
        inc.resolve(f.technician.getId(), "Paper jam cleared", now.plusSeconds(60));
        inc.verify(now.plusSeconds(120));
        f.service.closeIncident(new CloseIncidentCommand(f.admin.getId(), inc.getId(), "Resolved satisfactorily"));
        assertEquals(IncidentStatus.CLOSED, inc.getStatus());

        CheckDuplicateResponse res = duplicateService.checkDuplicates(new CheckDuplicateRequest(
                "Printer offline on 3rd floor",
                "Another printer issue",
                f.itService.getId(),
                f.category.getId()
        ));

        assertFalse(res.hasDuplicates());
        assertTrue(res.candidates().isEmpty());
    }

    @Test
    @DisplayName("candidate only exposes safe non-sensitive visibility fields")
    void candidate_safeFieldsOnly() {
        Incident inc = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(),
                "Email login failure for VIP user",
                "Private and confidential employee mailbox details",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                1
        ));

        CheckDuplicateResponse res = duplicateService.checkDuplicates(new CheckDuplicateRequest(
                "Email login failure",
                "My email fails",
                null,
                null
        ));

        assertTrue(res.hasDuplicates());
        DuplicateCandidateResponse candidate = res.candidates().get(0);
        assertEquals(inc.getId(), candidate.id());
        assertEquals("Email login failure for VIP user", candidate.title());
        assertNotNull(candidate.status());
        assertTrue(candidate.score() > 0);
        assertNotNull(candidate.matchReason());
    }

    @Test
    @DisplayName("limits candidates to top 5 ordered by descending score")
    void limitsCandidatesToTop5_descending() {
        for (int i = 1; i <= 8; i++) {
            f.service.createIncident(new CreateIncidentCommand(
                    f.employee.getId(),
                    "Network server latency issue variant " + i,
                    "Description " + i,
                    f.itService.getId(),
                    f.category.getId(),
                    null,
                    CategorySource.MANUAL,
                    Impact.LOW,
                    Urgency.LOW,
                    1
            ));
        }

        CheckDuplicateResponse res = duplicateService.checkDuplicates(new CheckDuplicateRequest(
                "Network server latency issue",
                "Query desc",
                f.itService.getId(),
                f.category.getId()
        ));

        assertTrue(res.hasDuplicates());
        assertTrue(res.candidates().size() <= 5);
        for (int i = 0; i < res.candidates().size() - 1; i++) {
            assertTrue(res.candidates().get(i).score() >= res.candidates().get(i + 1).score(),
                    "Candidates should be sorted descending by score");
        }
    }
}
