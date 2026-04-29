package com.example.banking.service;

import com.example.banking.dto.AccountDto;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.model.AccountEntity;
import com.example.banking.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** All accounts owned by the caller. May be empty. */
    public List<AccountDto> listForOwner(String ownerId) {
        // TODO: Call accounts.findByOwnerId(), and use .stream().map(AccountDto::from).toList() to map them.
        return null; // REPLACE THIS
    }

    /**
     * Looks up an account ONLY if the caller owns it. Returns 404 for both
     * non-existent and not-owned — never 403 — to avoid leaking the
     * existence of other users' accounts.
     */
    public AccountDto findOwnedAccount(String accountId, String callerUserId) {
        return AccountDto.from(loadOwned(accountId, callerUserId));
    }

    /** Internal helper for services that need the entity, not the DTO. */
    public AccountEntity loadOwned(String accountId, String callerUserId) {
        // TODO: Find the account by its accountId in the repository
        // TODO: Filter it to ensure the ownerId equals the callerUserId
        // TODO: OrElseThrow a new ResourceNotFoundException("account", accountId)
        return null; // REPLACE THIS
    }
}
