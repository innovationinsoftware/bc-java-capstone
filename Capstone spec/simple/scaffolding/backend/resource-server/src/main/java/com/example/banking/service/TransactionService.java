package com.example.banking.service;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.exception.BusinessRuleException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.kafka.TransactionEvent;
import com.example.banking.kafka.TransactionEventPublisher;
import com.example.banking.model.AccountEntity;
import com.example.banking.model.TransactionEntity;
import com.example.banking.model.TransactionStatus;
import com.example.banking.model.TransactionType;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Submits transactions and reads them back.
 *
 * Day-1 deliverable: implement DEPOSIT and WITHDRAWAL (see {@link #submit}).
 * Day-2 deliverable: implement TRANSFER_OUT for both internal (own account
 *   on both sides) and external (PaymentService call) cases.
 *
 * Every write goes through @Transactional so balance updates and
 * transaction-row inserts commit atomically. The Kafka publish happens
 * AFTER this method returns — see TransactionController and
 * TransactionEventPublisher.
 */
@Service
public class TransactionService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final PaymentService paymentService;
    private final TransactionEventPublisher publisher;

    public TransactionService(AccountRepository accounts,
                              TransactionRepository transactions,
                              AccountService accountService,
                              PaymentService paymentService,
                              TransactionEventPublisher publisher) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.accountService = accountService;
        this.paymentService = paymentService;
        this.publisher = publisher;
    }

    /** All transactions on an account, newest first. Ownership enforced. */
    public List<TransactionDto> listForOwnedAccount(String accountId, String callerUserId) {
        accountService.loadOwned(accountId, callerUserId); // throws 404 if not owned
        return transactions.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    /**
     * Submits a transaction and returns the persisted row(s).
     *
     * Returns a List because TRANSFER between two of the caller's own
     * accounts produces TWO rows. DEPOSIT/WITHDRAWAL/external transfer
     * return a list of one.
     *
     * Caller is responsible for publishing Kafka events for each returned
     * row AFTER this method commits. Don't publish from inside the
     * transaction — if the DB rolls back you'd emit a phantom event.
     */
    @Transactional
    public List<TransactionDto> submit(NewTransactionRequest req, String callerUserId) {
        // TODO: Implement transaction submission logic
        // 1. Verify the caller owns the source account
        // 2. Route the request based on the transaction type (DEPOSIT, WITHDRAWAL, TRANSFER_OUT)
        // 3. For WITHDRAWAL/TRANSFER_OUT, verify sufficient funds
        // 4. Update the account balance
        // 5. Persist the transaction row(s) to the database
        // 6. Return the persisted row(s) as DTOs
        // Note: For internal transfers (between two owned accounts), you must persist TWO rows.
        
        throw new UnsupportedOperationException("TODO: Implement transaction submission logic");
    }

    // TODO: Add private helper methods for DEPOSIT, WITHDRAWAL, and TRANSFER_OUT

    private void requireFunds(AccountEntity source, BigDecimal amount) {
        // TODO: Implement funds check. Throw InsufficientFundsException if balance < amount.
        throw new UnsupportedOperationException("TODO: Implement funds check");
    }

    private TransactionEntity persistRow(String accountId, TransactionType type,
                                         BigDecimal amount, TransactionStatus status,
                                         String counterparty, String transferGroupId,
                                         String description) {
        // TODO: Construct and save a new TransactionEntity
        throw new UnsupportedOperationException("TODO: Implement row persistence");
    }

    private TransactionType parseType(String raw) {
        try {
            return TransactionType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown transaction type: " + raw);
        }
    }

    /** Convenience for the controller — builds the event for an account it just acted on. */
    public TransactionEvent toEvent(TransactionDto tx, String ownerId, String currency) {
        // tx is a DTO; reconstruct just enough state to build an event payload
        return new TransactionEvent(
                "evt_" + UUID.randomUUID(),
                tx.transactionId(),
                tx.accountId(),
                ownerId,
                tx.type(),
                tx.amount(),
                currency,
                tx.status(),
                tx.counterparty(),
                tx.transferGroupId(),
                Instant.now()
        );
    }

    public void publishEvent(TransactionEvent event) {
        publisher.publish(event);
    }

    public TransactionDto findOwnedTransaction(String transactionId, String callerUserId) {
        TransactionEntity row = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("transaction", transactionId));
        accountService.loadOwned(row.getAccountId(), callerUserId); // 404 if not owned
        return TransactionDto.from(row);
    }
}
