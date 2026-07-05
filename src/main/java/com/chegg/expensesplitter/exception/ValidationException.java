package com.chegg.expensesplitter.exception;

/**
 * Thrown for domain/business-rule validation failures that should map to
 * HTTP 422 (Unprocessable Entity) - e.g. paidBy not a group member,
 * splitAmong containing a non-member. This is distinct from bean-validation
 * failures (missing/blank fields), which map to 400.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
