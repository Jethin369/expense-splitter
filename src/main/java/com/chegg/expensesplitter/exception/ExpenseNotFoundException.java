package com.chegg.expensesplitter.exception;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(Long expenseId) {
        super("Expense not found with id: " + expenseId);
    }
}
