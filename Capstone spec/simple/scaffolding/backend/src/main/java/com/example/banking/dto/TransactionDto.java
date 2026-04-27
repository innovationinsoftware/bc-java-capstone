package com.example.banking.dto;

import com.example.banking.model.TransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDto(
        String transactionId,
        String accountId,
        String type,
        BigDecimal amount,
        String status,
        String counterparty,
        String transferGroupId,
        String description,
        Instant createdAt
) {
    public static TransactionDto from(TransactionEntity tx) {
        return new TransactionDto(
                tx.getTransactionId(),
                tx.getAccountId(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getCounterparty(),
                tx.getTransferGroupId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }
}
