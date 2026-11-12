package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.repository.CategoryRepository;

/** JPA implementation of the domain {@link CategoryRepository}. */
@Repository
@Transactional
public class JpaCategoryRepository implements CategoryRepository {

    private final CategoryEntityRepository entities;

    public JpaCategoryRepository(CategoryEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public Category save(Category category) {
        if (category == null) {
            throw new InvalidInputException("category must not be null");
        }
        entities.save(CategoryEntity.from(category));
        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return entities.findById(id).map(CategoryEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return entities.findAll(Sort.by("id")).stream().map(CategoryEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAllActive() {
        return entities.findByActiveTrueOrderById().stream().map(CategoryEntity::toDomain).toList();
    }
}
