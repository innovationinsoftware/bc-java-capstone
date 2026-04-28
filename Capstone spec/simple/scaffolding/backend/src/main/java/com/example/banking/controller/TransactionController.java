package com.example.banking.controller;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.kafka.TransactionEvent;
import com.example.banking.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/{transactionId}")
    public TransactionDto getOne(@PathVariable String transactionId, Authentication auth) {
        return transactions.findOwnedTransaction(transactionId, auth.getName());
    }

    /**
     * Submit a transaction. Returns 201 with the created row(s).
     */
    @PostMapping
    public ResponseEntity<List<TransactionDto>> create(@Valid @RequestBody NewTransactionRequest req,
                                                       Authentication auth) {
        String callerUserId = auth.getName();
        
        // TODO: Call transactions.submit(req, callerUserId) to submit the transaction(s)
        List<TransactionDto> created = null; // REPLACE THIS
        
        // TODO: Iterate over the created transactions.
        //       For each row, use transactions.toEvent(...) to generate an event,
        //       then use transactions.publishEvent(...) to publish it to Kafka.

        // This code builds the 201 Created Location header. Uncomment once 'created' is populated.
        /*
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.get(0).transactionId())
                .toUri();
        return ResponseEntity.created(location).body(created);
        */
        
        return null; // REPLACE THIS once the above is un-commented
    }
}
