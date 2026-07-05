package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.BalanceResponse;
import com.chegg.expensesplitter.dto.SettlementResponse;
import com.chegg.expensesplitter.exception.ValidationException;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the balance/settlement business logic. Uses the real
 * Spring context wired against the in-memory H2 database so BalanceService is
 * exercised through GroupService/ExpenseService exactly as it runs in production.
 */
@SpringBootTest
@Transactional
class BalanceServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private BalanceService balanceService;

    private Group group;

    @BeforeEach
    void setUp() {
        group = groupService.createGroup("Goa Trip", Arrays.asList("Alice", "Bob", "Carol"));
    }

    private Map<String, BigDecimal> balancesAsMap(BalanceResponse response) {
        return response.getBalances().stream()
                .collect(Collectors.toMap(BalanceResponse.MemberBalance::getMember,
                        BalanceResponse.MemberBalance::getNetBalance));
    }

    @Test
    void balancesAreZeroWithNoExpenses() {
        BalanceResponse response = balanceService.computeBalances(group.getId());
        Map<String, BigDecimal> balances = balancesAsMap(response);

        assertThat(balances.get("Alice")).isEqualByComparingTo("0.00");
        assertThat(balances.get("Bob")).isEqualByComparingTo("0.00");
        assertThat(balances.get("Carol")).isEqualByComparingTo("0.00");
    }

    @Test
    void singleExpenseSplitEquallyAcrossAllMembers() {
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));

        assertThat(balances.get("Alice")).isEqualByComparingTo("2000.00");
        assertThat(balances.get("Bob")).isEqualByComparingTo("-1000.00");
        assertThat(balances.get("Carol")).isEqualByComparingTo("-1000.00");
    }

    @Test
    void multipleExpensesAcrossDifferentPayersNetOutCorrectly() {
        // Alice pays 3000 for hotel, split 3 ways -> Alice +2000, Bob -1000, Carol -1000
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));
        // Bob pays 300 for lunch, split 3 ways -> Bob +200, Alice -100, Carol -100
        expenseService.addExpense(group.getId(), "Lunch", new BigDecimal("300.00"), "Bob",
                Arrays.asList("Alice", "Bob", "Carol"));

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));

        assertThat(balances.get("Alice")).isEqualByComparingTo("1900.00");
        assertThat(balances.get("Bob")).isEqualByComparingTo("-800.00");
        assertThat(balances.get("Carol")).isEqualByComparingTo("-1100.00");

        // sanity check: balances must always net to zero
        BigDecimal total = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void unevenSplitDistributesRemainderCentsFairlyAndSumsExactly() {
        // 100.00 / 3 = 33.33 repeating -> the extra cent goes to the first
        // member in splitAmong order (Alice), so shares are 33.34/33.33/33.33.
        expenseService.addExpense(group.getId(), "Snacks", new BigDecimal("100.00"), "Carol",
                Arrays.asList("Alice", "Bob", "Carol"));

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));

        assertThat(balances.get("Alice")).isEqualByComparingTo("-33.34");
        assertThat(balances.get("Bob")).isEqualByComparingTo("-33.33");
        assertThat(balances.get("Carol")).isEqualByComparingTo("66.67"); // paid 100.00, owes 33.33

        BigDecimal total = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void memberWhoNeverPaysOrIsNeverSplitAmongStillAppearsWithZeroWhereApplicable() {
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("200.00"), "Alice",
                Arrays.asList("Alice", "Bob"));

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));

        // Carol was never involved in this expense - balance stays at 0
        assertThat(balances.get("Carol")).isEqualByComparingTo("0.00");
        assertThat(balances.get("Alice")).isEqualByComparingTo("100.00");
        assertThat(balances.get("Bob")).isEqualByComparingTo("-100.00");
    }

    @Test
    void oneMemberPaysAllExpensesInGroup() {
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("300.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));
        expenseService.addExpense(group.getId(), "Food", new BigDecimal("150.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));
        expenseService.addExpense(group.getId(), "Cabs", new BigDecimal("90.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));
        assertThat(balances.get("Alice")).isEqualByComparingTo("360.00");
        assertThat(balances.get("Bob")).isEqualByComparingTo("-180.00");
        assertThat(balances.get("Carol")).isEqualByComparingTo("-180.00");
    }

    @Test
    void settlementsMinimizeTransactionCount() {
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));

        SettlementResponse settlements = balanceService.computeSettlements(group.getId());

        // Alice +2000, Bob -1000, Carol -1000 -> exactly 2 transactions, both paying Alice
        assertThat(settlements.getSettlements()).hasSize(2);
        assertThat(settlements.getSettlements()).allMatch(t -> t.getTo().equals("Alice"));
        BigDecimal totalPaid = settlements.getSettlements().stream()
                .map(SettlementResponse.SettlementTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPaid).isEqualByComparingTo("2000.00");
    }

    @Test
    void settlementsResultInEveryoneSquare() {
        expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));
        expenseService.addExpense(group.getId(), "Lunch", new BigDecimal("300.00"), "Bob",
                Arrays.asList("Alice", "Bob", "Carol"));

        SettlementResponse settlements = balanceService.computeSettlements(group.getId());

        // Verify: applying every settlement transaction to the original balances zeroes everyone out.
        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));
        for (SettlementResponse.SettlementTransaction t : settlements.getSettlements()) {
            balances.merge(t.getFrom(), t.getAmount(), BigDecimal::add);
            balances.merge(t.getTo(), t.getAmount().negate(), BigDecimal::add);
        }
        for (BigDecimal remaining : balances.values()) {
            assertThat(remaining).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void deletingAnExpenseUpdatesBalances() {
        Expense expense = expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));
        expenseService.addExpense(group.getId(), "Lunch", new BigDecimal("300.00"), "Bob",
                Arrays.asList("Alice", "Bob", "Carol"));

        expenseService.deleteExpense(group.getId(), expense.getId());

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));
        // Only the lunch expense should remain: Bob +200, Alice -100, Carol -100
        assertThat(balances.get("Bob")).isEqualByComparingTo("200.00");
        assertThat(balances.get("Alice")).isEqualByComparingTo("-100.00");
        assertThat(balances.get("Carol")).isEqualByComparingTo("-100.00");
    }

    @Test
    void deletingTheOnlyExpenseResetsBalancesToZero() {
        Expense expense = expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("3000.00"), "Alice",
                Arrays.asList("Alice", "Bob", "Carol"));

        expenseService.deleteExpense(group.getId(), expense.getId());

        Map<String, BigDecimal> balances = balancesAsMap(balanceService.computeBalances(group.getId()));
        for (BigDecimal balance : balances.values()) {
            assertThat(balance).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void addingExpenseWithPaidByNotInGroupThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("100.00"), "Dave",
                        Arrays.asList("Alice", "Bob")));
    }

    @Test
    void addingExpenseWithSplitAmongContainingNonMemberThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("100.00"), "Alice",
                        Arrays.asList("Alice", "Dave")));
    }

    @Test
    void addingExpenseWithEmptySplitAmongThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                expenseService.addExpense(group.getId(), "Hotel", new BigDecimal("100.00"), "Alice",
                        List.of()));
    }
}
