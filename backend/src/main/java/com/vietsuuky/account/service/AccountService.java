package com.vietsuuky.account.service;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.entity.AccountStatus;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.common.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        return rawEmail.trim().toLowerCase();
    }

    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(normalizeEmail(email));
    }

    public Optional<Account> findById(UUID id) {
        return accountRepository.findById(id);
    }

    public boolean emailExists(String email) {
        return accountRepository.existsByEmail(normalizeEmail(email));
    }

    public Account createAccount(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new ApiException("Email is required");
        }
        if (emailExists(normalizedEmail)) {
            throw new ApiException("Email already exists");
        }

        Account account = new Account(normalizedEmail, passwordEncoder.encode(password));
        return accountRepository.save(account);
    }

    public Account getAccountOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("Account not found"));
    }

    public void ensureAccountActive(Account account) {
        if (account.getStatus() == AccountStatus.LOCKED || account.getStatus() == AccountStatus.DISABLED) {
            throw new ApiException("Account is locked or disabled");
        }
    }
}
