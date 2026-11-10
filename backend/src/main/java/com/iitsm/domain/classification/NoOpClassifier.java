package com.iitsm.domain.classification;

import java.util.Optional;

public final class NoOpClassifier implements CategoryClassifier {

    @Override
    public Optional<CategorySuggestion> classify(String title, String description) {
        return Optional.empty();
    }
}
