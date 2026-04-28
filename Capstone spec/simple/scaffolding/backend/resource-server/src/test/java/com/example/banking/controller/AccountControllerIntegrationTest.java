package com.example.banking.controller;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.kafka.TransactionEvent;
import com.example.banking.kafka.TransactionEventPublisher;
import com.example.banking.security.CustomOidcUserService;
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
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for auth, ownership, and the deposit happy path.
 *
 * In the BFF model the browser sends session cookies, not Bearer tokens.
 * Tests use .with(oauth2Login()) which places a simulated OAuth2 session
 * in the SecurityContext — equivalent to what .with(jwt()) did before.
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
    @MockBean CustomOidcUserService oidcUserService;
    @MockBean AccountService accountService;
    @MockBean TransactionService transactionService;
    @MockBean TransactionEventPublisher publisher;

    // ------------------------------------------------------------------ 401

    @Test
    void get_accounts_without_session_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
               .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 403

    @Test
    void customer_hitting_admin_endpoint_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                       .with(oauth2Login()
                           .authorities(new org.springframework.security.core.authority
                                   .SimpleGrantedAuthority("ROLE_CUSTOMER"))))
               .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ ownership (404)

    @Test
    void customer_hitting_other_users_account_returns_404() throws Exception {
        when(accountService.loadOwned(eq("acc_other"), any()))
                .thenThrow(new com.example.banking.exception.ResourceNotFoundException(
                        "account", "acc_other"));

        mockMvc.perform(get("/api/v1/accounts/acc_other")
                       .with(oauth2Login()
                           .authorities(new org.springframework.security.core.authority
                                   .SimpleGrantedAuthority("ROLE_CUSTOMER"))))
               .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ deposit happy path

    @Test
    void deposit_happy_path_returns_201_and_publishes_kafka_event() throws Exception {
        com.example.banking.dto.AccountDto account = new com.example.banking.dto.AccountDto(
                "acc_1", "CHECKING", "USD", new BigDecimal("1050.00"), LocalDateTime.now());

        TransactionDto txDto = new TransactionDto(
                "txn_1", "acc_1", "DEPOSIT", new BigDecimal("50.00"),
                "COMPLETED", null, null, "paycheck", LocalDateTime.now());

        when(transactionService.submit(any(NewTransactionRequest.class), any()))
                .thenReturn(List.of(txDto));
        when(transactionService.toEvent(any(), any(), eq("USD")))
                .thenReturn(new TransactionEvent("evt_1", "txn_1", "acc_1",
                        "usr_1", "DEPOSIT", new BigDecimal("50.00"),
                        "USD", "COMPLETED", null, null, Instant.now()));

        String body = mapper.writeValueAsString(
                new NewTransactionRequest("acc_1", "DEPOSIT",
                        new BigDecimal("50.00"), null, "paycheck"));

        mockMvc.perform(post("/api/v1/transactions")
                       .with(oauth2Login()
                           .authorities(new org.springframework.security.core.authority
                                   .SimpleGrantedAuthority("ROLE_CUSTOMER")))
                       .with(csrf())   // BFF requires CSRF token on mutating requests
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(body))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$[0].status").value("COMPLETED"))
               .andExpect(jsonPath("$[0].amount").value(50.00));

        verify(transactionService).publishEvent(any(TransactionEvent.class));
    }

    // ------------------------------------------------------------------ health (public)

    @Test
    void health_is_public_and_returns_200() throws Exception {
        mockMvc.perform(get("/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }
}
