package com.chegg.expensesplitter.controller;

import com.chegg.expensesplitter.dto.BalanceResponse;
import com.chegg.expensesplitter.dto.SettlementResponse;
import com.chegg.expensesplitter.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balances")
    public ResponseEntity<BalanceResponse> getBalances(@PathVariable Long groupId) {
        return ResponseEntity.ok(balanceService.computeBalances(groupId));
    }

    @GetMapping("/settlements")
    public ResponseEntity<SettlementResponse> getSettlements(@PathVariable Long groupId) {
        return ResponseEntity.ok(balanceService.computeSettlements(groupId));
    }
}
