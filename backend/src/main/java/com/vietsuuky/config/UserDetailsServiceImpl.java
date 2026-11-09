package com.vietsuuky.config;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AccountService accountService;

    public UserDetailsServiceImpl(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountService.findById(java.util.UUID.fromString(username))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return new User(account.getId().toString(), account.getPasswordHash(), Collections.emptyList());
    }
}
