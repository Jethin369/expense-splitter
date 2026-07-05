package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.BalanceResponse;
import com.chegg.expensesplitter.dto.SettlementResponse;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final GroupService groupService;
    private final ExpenseService expenseService;

    private static final int SCALE = 2;
    private static final BigDecimal CENT = new BigDecimal("0.01");

    public BalanceService(GroupService groupService, ExpenseService expenseService) {
        this.groupService = groupService;
        this.expenseService = expenseService;
    }

    /**
     * Computes each member's net balance on-the-fly from all expenses in the group.
     * Positive balance = member is owed money overall.
     * Negative balance = member owes money overall.
     * The balances always sum to (approximately) zero.
     */
    public BalanceResponse computeBalances(Long groupId) {
        Group group = groupService.getGroupOrThrow(groupId);
        List<Expense> expenses = expenseService.getExpensesForGroup(groupId);

        // Preserve group member order, initialise everyone at zero (so members who
        // never paid or were never split among still show up with a 0.00 balance).
        Map<String, BigDecimal> netBalances = new LinkedHashMap<>();
        for (String member : group.getMembers()) {
            netBalances.put(member, BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP));
        }

        for (Expense expense : expenses) {
            applyExpense(netBalances, expense);
        }

        List<BalanceResponse.MemberBalance> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : netBalances.entrySet()) {
            result.add(new BalanceResponse.MemberBalance(entry.getKey(), entry.getValue()));
        }
        return new BalanceResponse(result);
    }

    private void applyExpense(Map<String, BigDecimal> netBalances, Expense expense) {
        List<String> splitAmong = expense.getSplitAmong();
        BigDecimal amount = expense.getAmount();
        int n = splitAmong.size();

        // Equal split with fair-remainder distribution so shares always sum
        // exactly back to `amount`, even when it doesn't divide evenly.
        // e.g. 100.00 / 3 -> 33.34, 33.33, 33.33 (extra cent(s) go to the
        // first members in the list) instead of losing/gaining a paisa overall.
        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.DOWN);
        BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = amount.subtract(distributed); // e.g. 0.01, 0.02...
        int extraCents = remainder.divide(CENT, 0, RoundingMode.HALF_UP).intValue();

        // Credit the payer with the full amount they fronted.
        netBalances.merge(expense.getPaidBy(), amount, BigDecimal::add);

        // Debit each participant their share.
        for (int i = 0; i < n; i++) {
            String member = splitAmong.get(i);
            BigDecimal share = baseShare;
            if (i < extraCents) {
                share = share.add(CENT);
            }
            netBalances.merge(member, share.negate(), BigDecimal::add);
        }
    }

    /**
     * Settlement algorithm: greedy largest-debtor-pays-largest-creditor.
     *
     * 1. Split members into creditors (net balance > 0) and debtors (net balance < 0).
     * 2. Repeatedly take the debtor who owes the most and the creditor who is owed
     *    the most, and settle min(|debt|, credit) between them.
     * 3. Reduce both balances by that amount; whichever hits zero drops out.
     * 4. Repeat until all balances are zero.
     *
     * This does not guarantee the mathematically-minimal number of transactions
     * in every possible case, but for typical group-expense scenarios it produces
     * a small, sensible transaction count and is simple to reason about and test.
     */
    public SettlementResponse computeSettlements(Long groupId) {
        BalanceResponse balances = computeBalances(groupId);

        List<Balance> creditors = new ArrayList<>();
        List<Balance> debtors = new ArrayList<>();

        for (BalanceResponse.MemberBalance mb : balances.getBalances()) {
            BigDecimal amount = mb.getNetBalance();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new Balance(mb.getMember(), amount));
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new Balance(mb.getMember(), amount.abs()));
            }
        }

        List<SettlementResponse.SettlementTransaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((a, b) -> b.amount.compareTo(a.amount));
            debtors.sort((a, b) -> b.amount.compareTo(a.amount));

            Balance topCreditor = creditors.get(0);
            Balance topDebtor = debtors.get(0);

            BigDecimal settleAmount = topCreditor.amount.min(topDebtor.amount);

            transactions.add(new SettlementResponse.SettlementTransaction(
                    topDebtor.member, topCreditor.member, settleAmount));

            topCreditor.amount = topCreditor.amount.subtract(settleAmount);
            topDebtor.amount = topDebtor.amount.subtract(settleAmount);

            if (topCreditor.amount.compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(0);
            }
            if (topDebtor.amount.compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(0);
            }
        }

        return new SettlementResponse(transactions);
    }

    private static class Balance {
        String member;
        BigDecimal amount;

        Balance(String member, BigDecimal amount) {
            this.member = member;
            this.amount = amount;
        }
    }
}
