package com.example.banking.service;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.exception.BusinessRuleException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.PaymentProcessorException;
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
import java.time.LocalDateTime;
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

        TransactionType type = parseType(req.type());
        AccountEntity source = accountService.loadOwned(req.accountId(), callerUserId);

        return switch (type) {
            case DEPOSIT       -> List.of(applyDeposit(source, req));
            case WITHDRAWAL    -> List.of(applyWithdrawal(source, req));
            case TRANSFER_OUT  -> applyTransferOut(source, req, callerUserId);
            case TRANSFER_IN   -> throw new BusinessRuleException(
                    "TRANSFER_IN is created by the system; clients cannot post it directly");
        };
    }

    // ---- DEPOSIT --------------------------------------------------------

    private TransactionDto applyDeposit(AccountEntity source, NewTransactionRequest req) {
        if (req.counterparty() != null) {
            throw new BusinessRuleException("counterparty must be null for DEPOSIT");
        }
        source.setBalance(source.getBalance().add(req.amount()));
        accounts.save(source);
        TransactionEntity row = persistRow(source.getAccountId(), TransactionType.DEPOSIT,
                req.amount(), TransactionStatus.COMPLETED, null, null, req.description());
        return TransactionDto.from(row);
    }

    // ---- WITHDRAWAL -----------------------------------------------------

    private TransactionDto applyWithdrawal(AccountEntity source, NewTransactionRequest req) {
        if (req.counterparty() != null) {
            throw new BusinessRuleException("counterparty must be null for WITHDRAWAL");
        }
        requireFunds(source, req.amount());
        source.setBalance(source.getBalance().subtract(req.amount()));
        accounts.save(source);
        TransactionEntity row = persistRow(source.getAccountId(), TransactionType.WITHDRAWAL,
                req.amount(), TransactionStatus.COMPLETED, null, null, req.description());
        return TransactionDto.from(row);
    }

    // ---- TRANSFER_OUT ---------------------------------------------------

    private List<TransactionDto> applyTransferOut(AccountEntity source,
                                                  NewTransactionRequest req,
                                                  String callerUserId) {
        if (req.counterparty() == null || req.counterparty().isBlank()) {
            throw new BusinessRuleException("counterparty is required for TRANSFER_OUT");
        }
        requireFunds(source, req.amount());

        List<AccountEntity> ownedAccounts = accounts.findByOwnerId(callerUserId);
        boolean isInternal = ownedAccounts.stream()
                .anyMatch(a -> a.getAccountId().equals(req.counterparty()));

        if (isInternal) {
            AccountEntity destination = ownedAccounts.stream()
                    .filter(a -> a.getAccountId().equals(req.counterparty()))
                    .findFirst()
                    .orElseThrow(); // We know it's there

            String transferGroupId = "grp_" + UUID.randomUUID();

            // Debit source
            source.setBalance(source.getBalance().subtract(req.amount()));
            accounts.save(source);
            TransactionEntity outRow = persistRow(source.getAccountId(), TransactionType.TRANSFER_OUT,
                    req.amount(), TransactionStatus.COMPLETED, destination.getAccountId(), transferGroupId, req.description());

            // Credit destination
            destination.setBalance(destination.getBalance().add(req.amount()));
            accounts.save(destination);
            TransactionEntity inRow = persistRow(destination.getAccountId(), TransactionType.TRANSFER_IN,
                    req.amount(), TransactionStatus.COMPLETED, source.getAccountId(), transferGroupId, req.description());

            return List.of(TransactionDto.from(outRow), TransactionDto.from(inRow));
        } else {
            // External transfer
            String idempotencyKey = UUID.randomUUID().toString();
            try {
                paymentService.submitExternalTransfer(source.getAccountId(), req.counterparty(), req.amount(), "USD", idempotencyKey);
                
                // Success
                source.setBalance(source.getBalance().subtract(req.amount()));
                accounts.save(source);
                TransactionEntity row = persistRow(source.getAccountId(), TransactionType.TRANSFER_OUT,
                        req.amount(), TransactionStatus.COMPLETED, req.counterparty(), null, req.description());
                return List.of(TransactionDto.from(row));
            } catch (PaymentProcessorException e) {
                // Failure
                TransactionEntity row = persistRow(source.getAccountId(), TransactionType.TRANSFER_OUT,
                        req.amount(), TransactionStatus.FAILED, req.counterparty(), null, req.description());
                throw e; // Rethrow to let GlobalExceptionHandler handle it
            }
        }
    }

    // ---- helpers --------------------------------------------------------

    private void requireFunds(AccountEntity source, BigDecimal amount) {
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(source.getAccountId(),
                    source.getBalance(), amount);
        }
    }

    private TransactionEntity persistRow(String accountId, TransactionType type,
                                         BigDecimal amount, TransactionStatus status,
                                         String counterparty, String transferGroupId,
                                         String description) {
        TransactionEntity row = new TransactionEntity(
                "txn_" + UUID.randomUUID(),
                accountId,
                type,
                amount,
                status,
                counterparty,
                transferGroupId,
                description,
                LocalDateTime.now()
        );
        return transactions.save(row);
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
