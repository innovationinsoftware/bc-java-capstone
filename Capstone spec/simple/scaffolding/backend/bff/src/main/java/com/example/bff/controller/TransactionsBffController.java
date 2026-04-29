package com.example.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionsBffController {

    private final WebClient resourceServerWebClient;

    public TransactionsBffController(WebClient resourceServerWebClient) {
        this.resourceServerWebClient = resourceServerWebClient;
    }

    @GetMapping("/{transactionId}")
    public Mono<JsonNode> getTransaction(@PathVariable String transactionId) {
        return resourceServerWebClient.get()
                .uri("/api/v1/transactions/{id}", transactionId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Forwards the SPA's submission to the Resource Server. The WebClient
     * handles the bearer header; we forward the response status and body
     * including any 4xx/5xx error envelopes (RFC 7807) the RS produces.
     */
    @PostMapping
    public Mono<ResponseEntity<JsonNode>> create(@RequestBody JsonNode body) {
        return resourceServerWebClient.post()
                .uri("/api/v1/transactions")
                .bodyValue(body)
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
