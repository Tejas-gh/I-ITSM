package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.repository.CategoryRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link CategoryRepository} backed by a {@link LinkedHashMap}.
 */
public final class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<String, Category> storage = new LinkedHashMap<>();

    @Override
    public synchronized Category save(Category category) {
        if (category == null) {
            throw new InvalidInputException("category must not be null");
        }
        storage.put(category.getId(), category);
        return category;
    }

    @Override
    public synchronized Optional<Category> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<Category> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage.values()));
    }

    @Override
    public synchronized List<Category> findAllActive() {
        List<Category> active = new ArrayList<>();
        for (Category category : storage.values()) {
            if (category.isActive()) {
                active.add(category);
            }
        }
        return Collections.unmodifiableList(active);
    }
}

