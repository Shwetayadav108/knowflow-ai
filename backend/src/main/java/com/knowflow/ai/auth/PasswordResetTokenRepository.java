package com.knowflow.ai.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PasswordResetToken p WHERE p.user.id = :userId AND p.status = :status ORDER BY p.createdAt DESC")
    List<PasswordResetToken> findByUserIdAndStatusForUpdate(
            @Param("userId") UUID userId,
            @Param("status") PasswordResetTokenStatus status
    );

    Optional<PasswordResetToken> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PasswordResetTokenStatus status);

    List<PasswordResetToken> findAllByUserIdAndStatus(UUID userId, PasswordResetTokenStatus status);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.status = 'REVOKED' WHERE p.user.id = :userId AND p.status = 'PENDING'")
    void revokeAllPendingByUserId(@Param("userId") UUID userId);

    Optional<PasswordResetToken> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, PasswordResetTokenStatus status);
}
