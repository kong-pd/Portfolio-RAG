package com.portfolio.rag.common;

/**
 * Thrown when a resource does not exist OR belongs to another user.
 * Always mapped to 404 NOT_FOUND (never 403, per API contract D-03).
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
