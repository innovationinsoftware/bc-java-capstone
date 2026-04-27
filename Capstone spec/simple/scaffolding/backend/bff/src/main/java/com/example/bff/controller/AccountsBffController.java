package com.example.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Thin proxy: SPA → BFF → Resource Server.
 *
 * The WebClient here is the OAuth2-enabled bean from WebClientConfig. The
 * filter attaches the user's bearer token automatically — nothing in this
 * code touches tokens.
 *
 * We use JsonNode as the response type to avoid duplicating DTOs in the
 * BFF. The Resource Server owns the schema; the BFF just forwards bytes.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountsBffController {

    private final WebClient resourceServerWebClient;

    public AccountsBffController(WebClient resourceServerWebClient) {
        this.resourceServerWebClient = resourceServerWebClient;
    }

    @GetMapping
    public Mono<JsonNode> listAccounts() {
        return resourceServerWebClient.get()
                .uri("/api/v1/accounts")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    @GetMapping("/{accountId}")
    public Mono<JsonNode> getAccount(@PathVariable String accountId) {
        return resourceServerWebClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    @GetMapping("/{accountId}/transactions")
    public Mono<JsonNode> getTransactions(@PathVariable String accountId) {
        return resourceServerWebClient.get()
                .uri("/api/v1/accounts/{id}/transactions", accountId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
