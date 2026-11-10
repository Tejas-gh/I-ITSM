package com.iitsm.domain.classification;

import java.util.Optional;

public interface CategoryClassifier {

    Optional<CategorySuggestion> classify(String title, String description);
}
