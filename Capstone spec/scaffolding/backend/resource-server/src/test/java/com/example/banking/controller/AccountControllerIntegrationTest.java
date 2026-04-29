package com.example.banking.controller;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.kafka.TransactionEvent;
import com.example.banking.kafka.TransactionEventPublisher;
import com.example.banking.service.AccountService;
import com.example.banking.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Resource Server.
 *
 * Uses .with(jwt()) to simulate a Bearer token — same as calling the RS
 * via the BFF's WebClient with an OAuth2 filter. No real auth server needed.
 *
 * @EmbeddedKafka spins up an in-process broker for the Kafka emission test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"transactions.completed"})
class AccountControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    // Mocked so the tests don't need a real Oracle DB
    @MockBean AccountService accountService;
    @MockBean TransactionService transactionService;
    @MockBean TransactionEventPublisher publisher;

    // ------------------------------------------------------------------ 401

    @Test
    void get_accounts_without_token_returns_401() throws Exception {
        /*
         * TODO (Day 2 — Step 2a): Test that an unauthenticated GET to /api/v1/accounts
         * returns 401 Unauthorized.
         *
         * Use: mockMvc.perform(get("/api/v1/accounts"))
         *              .andExpect(status().isUnauthorized())
         *
         * This verifies that SecurityConfig correctly requires authentication.
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ 403

    @Test
    void customer_hitting_admin_endpoint_returns_403() throws Exception {
        /*
         * TODO (Day 2 — Step 2b): Test that a CUSTOMER token is denied access to
         * /api/v1/admin/users with 403 Forbidden.
         *
         * Use .with(jwt()...) to simulate a JWT token with ROLE_CUSTOMER authority.
         * The JWT builder pattern:
         *   jwt().jwt(j -> j.subject("sub").claim("email", "x@y.com").claim("role", "CUSTOMER"))
         *        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
         *
         * Expect: status().isForbidden()
         *
         * This verifies the hasRole("ADMIN") rule in SecurityConfig AND
         * the @PreAuthorize("hasRole('ADMIN')") on UserController.
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ ownership (404)

    @Test
    void customer_hitting_other_users_account_returns_404() throws Exception {
        /*
         * TODO (Day 2 — Step 2c): Test the "safe 404" ownership rule.
         *
         * When accountService.loadOwned("acc_other", anyString()) throws
         * ResourceNotFoundException, the controller must return 404.
         *
         * Setup:
         *   when(accountService.loadOwned(eq("acc_other"), any()))
         *       .thenThrow(new ResourceNotFoundException("account", "acc_other"))
         *
         * The rule: a non-owned account returns 404, NOT 403.
         * This prevents an attacker from learning which account IDs exist.
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ deposit happy path

    @Test
    void deposit_happy_path_returns_201_and_publishes_kafka_event() throws Exception {
        /*
         * TODO (Day 1 — Step 4): Integration test for the deposit happy path.
         *
         * This test verifies the full HTTP layer:
         *   1. POST to /api/v1/transactions returns 201 Created
         *   2. Response body contains the transaction row with status=COMPLETED
         *   3. The controller calls publisher.publishEvent() exactly once
         *
         * Setup:
         *   - Create a TransactionDto stub representing the completed deposit
         *   - Stub transactionService.submit(...) to return List.of(txDto)
         *   - Stub transactionService.toEvent(...) to return a TransactionEvent
         *
         * Request:
         *   - POST /api/v1/transactions
         *   - .with(jwt()...) with ROLE_CUSTOMER
         *   - Content-Type: application/json
         *   - Body: serialise a NewTransactionRequest("acc_1", "DEPOSIT", 50.00, null, "paycheck")
         *
         * Assertions:
         *   - status().isCreated()
         *   - jsonPath("$[0].status").value("COMPLETED")
         *   - jsonPath("$[0].amount").value(50.00)
         *   - verify(transactionService).publishEvent(any(TransactionEvent.class))
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ internal transfer creates two rows

    @Test
    void internal_transfer_returns_201_with_two_transaction_rows() throws Exception {
        /*
         * TODO (Day 2 — Step 4d): Integration test for internal transfer response shape.
         *
         * Stub transactionService.submit to return two rows:
         *   outRow: type=TRANSFER_OUT, accountId=acc_src, counterparty=acc_dst,
         *           transferGroupId=grp_abc, status=COMPLETED
         *   inRow:  type=TRANSFER_IN,  accountId=acc_dst, counterparty=acc_src,
         *           transferGroupId=grp_abc, status=COMPLETED
         *
         * POST /api/v1/transactions with TRANSFER_OUT body
         *
         * Assert:
         *   - status 201
         *   - $.length() == 2
         *   - $[0].type == "TRANSFER_OUT"
         *   - $[1].type == "TRANSFER_IN"
         *   - both transferGroupId values == "grp_abc"
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ external transfer 503 → 502

    @Test
    void external_transfer_processor_503_returns_502() throws Exception {
        /*
         * TODO (Day 2 — Step 4e): Test that a PaymentProcessorException from the
         * service is mapped to HTTP 502 Bad Gateway by GlobalExceptionHandler.
         *
         * Setup: stub transactionService.submit(...) to throw PaymentProcessorException
         *
         * Assert:
         *   - status().isBadGateway()
         *   - jsonPath("$.code").value("PAYMENT_PROCESSOR_ERROR")
         *
         * This verifies the GlobalExceptionHandler exception mapping.
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ health (public)

    @Test
    void health_is_public_and_returns_200() throws Exception {
        /*
         * TODO (Day 2 — Step 2d): Test that GET /health is accessible without a token.
         *
         * This verifies that .requestMatchers(GET, "/health").permitAll() works.
         *
         * Use: mockMvc.perform(get("/health"))
         *              .andExpect(status().isOk())
         *              .andExpect(jsonPath("$.status").value("UP"))
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }
}
