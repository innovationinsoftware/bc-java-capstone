package com.example.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class UsersBffController {

    private final WebClient resourceServerWebClient;

    public UsersBffController(WebClient resourceServerWebClient) {
        this.resourceServerWebClient = resourceServerWebClient;
    }

    @GetMapping("/users/me")
    public Mono<JsonNode> me() {
        return resourceServerWebClient.get()
                .uri("/api/v1/users/me")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /** ADMIN-only: the gate is enforced by the Resource Server. */
    @GetMapping("/admin/users")
    public Mono<JsonNode> listAllUsers() {
        return resourceServerWebClient.get()
                .uri("/api/v1/admin/users")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
