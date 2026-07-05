package com.chegg.expensesplitter.dto;

import java.math.BigDecimal;
import java.util.List;

public class SettlementResponse {

    private List<SettlementTransaction> settlements;

    public SettlementResponse() {
    }

    public SettlementResponse(List<SettlementTransaction> settlements) {
        this.settlements = settlements;
    }

    public List<SettlementTransaction> getSettlements() {
        return settlements;
    }

    public void setSettlements(List<SettlementTransaction> settlements) {
        this.settlements = settlements;
    }

    public static class SettlementTransaction {
        private String from;
        private String to;
        private BigDecimal amount;

        public SettlementTransaction() {
        }

        public SettlementTransaction(String from, String to, BigDecimal amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
