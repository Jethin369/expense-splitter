package com.chegg.expensesplitter.exception;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long groupId) {
        super("Group not found with id: " + groupId);
    }
}
