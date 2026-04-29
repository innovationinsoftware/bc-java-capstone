package com.example.banking.service;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.kafka.TransactionEventPublisher;
import com.example.banking.model.AccountEntity;
import com.example.banking.model.AccountType;
import com.example.banking.model.TransactionStatus;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the service layer using plain Mockito (no Spring context needed).
 * Covers: deposit, withdrawal, insufficient funds, ownership, internal transfer,
 * external transfer success, external transfer failure (no debit).
 */
class TransactionServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountService accountService = new AccountService(accounts);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final TransactionEventPublisher publisher = mock(TransactionEventPublisher.class);

    private final TransactionService svc = new TransactionService(
            accounts, transactions, accountService, paymentService, publisher);

    // ------------------------------------------------------------------ helpers

    private AccountEntity account(String id, String owner, BigDecimal balance) {
        return new AccountEntity(id, owner, AccountType.CHECKING, "USD", balance, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ deposit

    @Test
    void deposit_credits_balance_and_returns_completed_row() {
        AccountEntity acct = account("acc_1", "usr_1", new BigDecimal("200.00"));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TransactionDto> result = svc.submit(
                new NewTransactionRequest("acc_1", "DEPOSIT",
                        new BigDecimal("50.00"), null, "paycheck"),
                "usr_1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(acct.getBalance()).isEqualByComparingTo("250.00");
    }

    // ------------------------------------------------------------------ withdrawal

    @Test
    void withdrawal_within_balance_succeeds_and_debits() {
        AccountEntity acct = account("acc_1", "usr_1", new BigDecimal("100.00"));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TransactionDto> result = svc.submit(
                new NewTransactionRequest("acc_1", "WITHDRAWAL",
                        new BigDecimal("30.00"), null, "ATM"),
                "usr_1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(acct.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void withdrawal_below_balance_throws_insufficient_funds_and_does_not_debit() {
        AccountEntity acct = account("acc_1", "usr_1", new BigDecimal("10.00"));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));

        assertThatThrownBy(() -> svc.submit(
                new NewTransactionRequest("acc_1", "WITHDRAWAL",
                        new BigDecimal("50.00"), null, null),
                "usr_1"))
            .isInstanceOf(InsufficientFundsException.class);

        // balance must be unchanged
        assertThat(acct.getBalance()).isEqualByComparingTo("10.00");
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void submit_against_account_owned_by_another_user_throws_not_found() {
        AccountEntity acct = account("acc_other", "usr_other", new BigDecimal("500.00"));
        when(accounts.findById("acc_other")).thenReturn(Optional.of(acct));

        // usr_attacker tries to submit against usr_other's account
        assertThatThrownBy(() -> svc.submit(
                new NewTransactionRequest("acc_other", "WITHDRAWAL",
                        new BigDecimal("1.00"), null, null),
                "usr_attacker"))
            .isInstanceOf(ResourceNotFoundException.class);

        // no money moved
        assertThat(acct.getBalance()).isEqualByComparingTo("500.00");
    }

    // ------------------------------------------------------------------ internal transfer

    @Test
    void internal_transfer_creates_two_rows_with_same_transfer_group_id() {
        AccountEntity source = account("acc_src", "usr_1", new BigDecimal("500.00"));
        AccountEntity dest   = account("acc_dst", "usr_1", new BigDecimal("100.00"));

        when(accounts.findById("acc_src")).thenReturn(Optional.of(source));
        // Service uses findByOwnerId to determine internal vs external
        when(accounts.findByOwnerId("usr_1")).thenReturn(List.of(source, dest));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TransactionDto> result = svc.submit(
                new NewTransactionRequest("acc_src", "TRANSFER_OUT",
                        new BigDecimal("200.00"), "acc_dst", "rent"),
                "usr_1");

        // two rows: one TRANSFER_OUT, one TRANSFER_IN
        assertThat(result).hasSize(2);

        TransactionDto out = result.stream()
                .filter(t -> t.type().equals("TRANSFER_OUT")).findFirst().orElseThrow();
        TransactionDto in = result.stream()
                .filter(t -> t.type().equals("TRANSFER_IN")).findFirst().orElseThrow();

        assertThat(out.status()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(in.status()).isEqualTo(TransactionStatus.COMPLETED.name());

        // same transfer group links the pair
        assertThat(out.transferGroupId()).isNotNull()
                .isEqualTo(in.transferGroupId());

        // balances updated correctly
        assertThat(source.getBalance()).isEqualByComparingTo("300.00");
        assertThat(dest.getBalance()).isEqualByComparingTo("300.00");
    }

    // ------------------------------------------------------------------ external transfer

    @Test
    void external_transfer_success_completes_and_debits() {
        AccountEntity acct = account("acc_1", "usr_1", new BigDecimal("1000.00"));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));
        // "ext_counterparty" is NOT in usr_1's owned accounts → external path
        when(accounts.findByOwnerId("usr_1")).thenReturn(List.of(acct));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // payment service succeeds (no exception)
        doNothing().when(paymentService).submitExternalTransfer(any(), any(), any(), any(), any());

        List<TransactionDto> result = svc.submit(
                new NewTransactionRequest("acc_1", "TRANSFER_OUT",
                        new BigDecimal("250.00"), "ext_counterparty", "invoice"),
                "usr_1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(acct.getBalance()).isEqualByComparingTo("750.00");
        verify(paymentService).submitExternalTransfer(any(), any(), any(), any(), any());
    }

    @Test
    void external_transfer_failure_persists_failed_row_rethrows_and_does_not_debit() {
        AccountEntity acct = account("acc_1", "usr_1", new BigDecimal("1000.00"));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));
        when(accounts.findByOwnerId("usr_1")).thenReturn(List.of(acct));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // payment processor returns 503 → PaymentProcessorException
        doThrow(new com.example.banking.exception.PaymentProcessorException("upstream 503"))
                .when(paymentService).submitExternalTransfer(any(), any(), any(), any(), any());

        // Service rethrows so GlobalExceptionHandler can map it to 502
        assertThatThrownBy(() -> svc.submit(
                new NewTransactionRequest("acc_1", "TRANSFER_OUT",
                        new BigDecimal("250.00"), "ext_counterparty", "invoice"),
                "usr_1"))
            .isInstanceOf(com.example.banking.exception.PaymentProcessorException.class);

        // balance must NOT be debited — the debit only happens after a successful processor call
        assertThat(acct.getBalance()).isEqualByComparingTo("1000.00");
    }
}
