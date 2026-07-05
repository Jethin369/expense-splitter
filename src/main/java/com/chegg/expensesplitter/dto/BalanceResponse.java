package com.chegg.expensesplitter.dto;

import java.math.BigDecimal;
import java.util.List;

public class BalanceResponse {

    private List<MemberBalance> balances;

    public BalanceResponse() {
    }

    public BalanceResponse(List<MemberBalance> balances) {
        this.balances = balances;
    }

    public List<MemberBalance> getBalances() {
        return balances;
    }

    public void setBalances(List<MemberBalance> balances) {
        this.balances = balances;
    }

    public static class MemberBalance {
        private String member;
        private BigDecimal netBalance;

        public MemberBalance() {
        }

        public MemberBalance(String member, BigDecimal netBalance) {
            this.member = member;
            this.netBalance = netBalance;
        }

        public String getMember() {
            return member;
        }

        public void setMember(String member) {
            this.member = member;
        }

        public BigDecimal getNetBalance() {
            return netBalance;
        }

        public void setNetBalance(BigDecimal netBalance) {
            this.netBalance = netBalance;
        }
    }
}
