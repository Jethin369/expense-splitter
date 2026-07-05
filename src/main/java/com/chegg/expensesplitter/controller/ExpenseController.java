package com.chegg.expensesplitter.controller;

import com.chegg.expensesplitter.dto.AddExpenseRequest;
import com.chegg.expensesplitter.dto.ExpenseResponse;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> addExpense(@PathVariable Long groupId,
                                                       @Valid @RequestBody AddExpenseRequest request) {
        Expense expense = expenseService.addExpense(
                groupId,
                request.getTitle(),
                request.getAmount(),
                request.getPaidBy(),
                request.getSplitAmong()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ExpenseResponse.fromEntity(expense));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> listExpenses(@PathVariable Long groupId) {
        List<ExpenseResponse> expenses = expenseService.getExpensesForGroup(groupId).stream()
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(expenses);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        expenseService.deleteExpense(groupId, expenseId);
        return ResponseEntity.noContent().build();
    }
}
