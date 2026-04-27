package com.example.banking.service;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.kafka.TransactionEventPublisher;
import com.example.banking.model.AccountEntity;
import com.example.banking.model.AccountType;
import com.example.banking.model.TransactionEntity;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Example unit tests for the service layer using plain Mockito. Use these
 * as a starting point — extend them to cover deposit, transfer (internal
 * + external), ownership violation, and the no-debit-on-failure path.
 */
class TransactionServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AccountService accountService = new AccountService(accounts);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final TransactionEventPublisher publisher = mock(TransactionEventPublisher.class);

    private final TransactionService svc = new TransactionService(
            accounts, transactions, accountService, paymentService, publisher);

    @Test
    void withdrawal_within_balance_succeeds_and_debits() {
        AccountEntity acct = new AccountEntity("acc_1", "usr_1", AccountType.CHECKING,
                "USD", new BigDecimal("100.00"), Instant.now());
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TransactionDto> result = svc.submit(
                new NewTransactionRequest("acc_1", "WITHDRAWAL",
                        new BigDecimal("30.00"), null, "ATM"),
                "usr_1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("COMPLETED");
        assertThat(acct.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void withdrawal_below_balance_throws_insufficient_funds_and_does_not_debit() {
        AccountEntity acct = new AccountEntity("acc_1", "usr_1", AccountType.CHECKING,
                "USD", new BigDecimal("10.00"), Instant.now());
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));

        assertThatThrownBy(() -> svc.submit(
                new NewTransactionRequest("acc_1", "WITHDRAWAL",
                        new BigDecimal("50.00"), null, null),
                "usr_1"))
            .isInstanceOf(InsufficientFundsException.class);

        // balance unchanged
        assertThat(acct.getBalance()).isEqualByComparingTo("10.00");
    }

    // TODO: deposit happy path
    // TODO: ownership violation (loadOwned throws ResourceNotFoundException)
    // TODO: TRANSFER_OUT internal — both rows persisted, same transferGroupId
    // TODO: TRANSFER_OUT external success — payment service called, one row COMPLETED
    // TODO: TRANSFER_OUT external failure — payment service throws, FAILED row, no debit
}
