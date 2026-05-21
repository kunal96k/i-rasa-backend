package com.perfume.rasa.exception;

/**
 * Exception thrown when data integrity constraints are violated
 */
public class DataIntegrityException extends RuntimeException {
    private final String entity;
    private final String issue;

    public DataIntegrityException(String entity, String issue) {
        super(String.format("Data integrity violation in %s: %s", entity, issue));
        this.entity = entity;
        this.issue = issue;
    }

    public DataIntegrityException(String message) {
        super(message);
        this.entity = null;
        this.issue = null;
    }

    public DataIntegrityException(String message, Throwable cause) {
        super(message, cause);
        this.entity = null;
        this.issue = null;
    }

    public String getEntity() {
        return entity;
    }

    public String getIssue() {
        return issue;
    }
}
