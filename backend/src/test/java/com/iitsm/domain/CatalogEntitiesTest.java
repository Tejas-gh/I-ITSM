package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogEntitiesTest {

    @Test
    void itService_invalidArguments_throwInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new ItService(null, "VPN", BusinessCriticality.HIGH));
        assertThrows(InvalidInputException.class, () -> new ItService(" ", "VPN", BusinessCriticality.HIGH));
        assertThrows(InvalidInputException.class, () -> new ItService("S1", null, BusinessCriticality.HIGH));
        assertThrows(InvalidInputException.class, () -> new ItService("S1", "  ", BusinessCriticality.HIGH));
        assertThrows(InvalidInputException.class, () -> new ItService("S1", "VPN", null));
    }

    @Test
    void itService_validAndEquality_byIdOnly() {
        ItService a = new ItService(" S1 ", " VPN ", BusinessCriticality.CRITICAL);
        ItService b = new ItService("S1", "Other name", BusinessCriticality.LOW);

        assertEquals("S1", a.getId());
        assertEquals("VPN", a.getName());
        assertEquals(BusinessCriticality.CRITICAL, a.getBusinessCriticality());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ItService("S2", "VPN", BusinessCriticality.CRITICAL));
    }

    @Test
    void category_idAndNameRules() {
        assertThrows(InvalidInputException.class, () -> new Category(null, "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("network", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("1NETWORK", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("_NETWORK", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("NET WORK", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("NET-WORK", "Network"));
        assertThrows(InvalidInputException.class, () -> new Category("NETWORK", null));
        assertThrows(InvalidInputException.class, () -> new Category("NETWORK", "   "));

        Category ok = new Category("CLOUD_2", " Cloud ");
        assertEquals("CLOUD_2", ok.getId());
        assertEquals("Cloud", ok.getName());
        assertTrue(ok.isActive());
        assertEquals("A", new Category("A", "A").getId());
    }

    @Test
    void category_equalityByIdAndActivation() {
        Category a = new Category("NETWORK", "Network");
        Category b = new Category("NETWORK", "Different");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Category("DATABASE", "Network"));

        a.deactivate();
        assertFalse(a.isActive());
        a.activate();
        assertTrue(a.isActive());
    }

    @Test
    void slaPolicy_validPolicy_isDefaultDependsOnServiceId() {
        SlaPolicy def = new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        SlaPolicy override = new SlaPolicy("S1", PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2));

        assertTrue(def.isDefault());
        assertFalse(override.isDefault());
        assertEquals("S1", override.serviceId());
        assertEquals(PriorityLevel.P2, override.priority());
        assertEquals(Duration.ofMinutes(15), override.responseTarget());
        assertEquals(Duration.ofHours(2), override.resolutionTarget());
    }

    @Test
    void slaPolicy_targetsMustBePositive() {
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ZERO, Duration.ofMinutes(30)));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(-1), Duration.ofMinutes(30)));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ZERO));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(-30)));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, null, Duration.ofMinutes(30)));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), null));
    }

    @Test
    void slaPolicy_responseMustNotExceedResolution_equalAllowed() {
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(31), Duration.ofMinutes(30)));
        SlaPolicy equal = new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(30), Duration.ofMinutes(30));
        assertEquals(equal.responseTarget(), equal.resolutionTarget());
    }

    @Test
    void slaPolicy_nullPriority_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy(null, null, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        assertThrows(InvalidInputException.class,
                () -> new SlaPolicy("S1", null, Duration.ofMinutes(5), Duration.ofMinutes(30)));
    }
}
