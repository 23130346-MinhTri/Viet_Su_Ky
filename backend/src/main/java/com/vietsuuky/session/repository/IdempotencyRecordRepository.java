package com.vietsuuky.session.repository;

import com.vietsuuky.session.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByOperationNameAndIdempotencyKey(String operationName, String idempotencyKey);
    Optional<IdempotencyRecord> findByPlayerIdAndSessionIdAndOperationName(UUID playerId, UUID sessionId, String operationName);
}
