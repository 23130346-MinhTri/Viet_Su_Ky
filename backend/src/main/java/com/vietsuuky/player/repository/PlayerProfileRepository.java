package com.vietsuuky.player.repository;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.player.entity.PlayerProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {
    Optional<PlayerProfile> findByAccount(Account account);
    @EntityGraph(attributePaths = "account")
    Optional<PlayerProfile> findWithAccountByAccount_Id(UUID accountId);
    Optional<PlayerProfile> findByAccount_Id(UUID accountId);
    boolean existsByAccount(Account account);
}
