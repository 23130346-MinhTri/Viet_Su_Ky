package com.vietsuuky.player.repository;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.player.entity.PlayerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {
    Optional<PlayerProfile> findByAccount(Account account);
    @EntityGraph(attributePaths = "account")
    Optional<PlayerProfile> findWithAccountByAccount_Id(UUID accountId);
    Optional<PlayerProfile> findByAccount_Id(UUID accountId);
    boolean existsByAccount(Account account);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlayerProfile p where p.id = :playerId")
    Optional<PlayerProfile> findForUpdateById(@Param("playerId") UUID playerId);
}
