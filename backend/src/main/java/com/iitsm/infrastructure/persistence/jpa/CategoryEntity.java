package com.iitsm.infrastructure.persistence.jpa;

import com.iitsm.domain.model.Category;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code categories}; infrastructure-only, mapped explicitly to/from the domain. */
@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    String id;
    String name;
    boolean active;

    protected CategoryEntity() {
    }

    static CategoryEntity from(Category category) {
        CategoryEntity e = new CategoryEntity();
        e.id = category.getId();
        e.name = category.getName();
        e.active = category.isActive();
        return e;
    }

    Category toDomain() {
        Category category = new Category(id, name);
        if (!active) {
            category.deactivate();
        }
        return category;
    }
}
