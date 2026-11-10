package com.iitsm.domain.exception;

public class CategoryNotFoundException extends ItsmException {
    public CategoryNotFoundException(String message) {
        super("CATEGORY_NOT_FOUND", message);
    }
}
