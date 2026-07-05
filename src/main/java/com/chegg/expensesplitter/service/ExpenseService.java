package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.exception.ExpenseNotFoundException;
import com.chegg.expensesplitter.exception.ValidationException;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
    }

    public Expense addExpense(Long groupId, String title, BigDecimal amount, String paidBy, List<String> splitAmong) {
        Group group = groupService.getGroupOrThrow(groupId);

        if (!group.getMembers().contains(paidBy)) {
            throw new ValidationException("paidBy '" + paidBy + "' is not a member of this group");
        }

        if (splitAmong == null || splitAmong.isEmpty()) {
            throw new ValidationException("splitAmong must not be empty");
        }

        for (String member : splitAmong) {
            if (!group.getMembers().contains(member)) {
                throw new ValidationException("splitAmong contains '" + member + "' who is not a member of this group");
            }
        }

        Expense expense = new Expense(group, title, amount, paidBy, splitAmong);
        return expenseRepository.save(expense);
    }

    public List<Expense> getExpensesForGroup(Long groupId) {
        // ensure the group exists (throws 404 otherwise)
        groupService.getGroupOrThrow(groupId);
        return expenseRepository.findByGroupId(groupId);
    }

    public void deleteExpense(Long groupId, Long expenseId) {
        groupService.getGroupOrThrow(groupId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        if (!expense.getGroup().getId().equals(groupId)) {
            throw new ExpenseNotFoundException(expenseId);
        }

        expenseRepository.delete(expense);
    }
}
