package com.iitsm.infrastructure.persistence.jpa;

import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.ItService;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code it_services}. */
@Entity
@Table(name = "it_services")
public class ItServiceEntity {

    @Id
    String id;
    String name;
    @Enumerated(EnumType.STRING)
    BusinessCriticality businessCriticality;

    protected ItServiceEntity() {
    }

    static ItServiceEntity from(ItService service) {
        ItServiceEntity e = new ItServiceEntity();
        e.id = service.getId();
        e.name = service.getName();
        e.businessCriticality = service.getBusinessCriticality();
        return e;
    }

    ItService toDomain() {
        return new ItService(id, name, businessCriticality);
    }
}
