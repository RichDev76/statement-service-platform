package com.example.statementservice.repository;

import com.example.statementservice.model.entity.SignedLink;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignedLinkRepository extends JpaRepository<SignedLink, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SignedLink s WHERE s.token = :token")
    Optional<SignedLink> findByTokenForUpdate(@Param("token") String token);

    @Modifying
    @Query("UPDATE SignedLink s SET s.used = true WHERE s.token = :token AND s.singleUse = true AND s.used = false")
    int consumeSingleUse(@Param("token") String token);
}
