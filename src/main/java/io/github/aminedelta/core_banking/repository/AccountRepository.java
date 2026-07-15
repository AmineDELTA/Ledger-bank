package io.github.aminedelta.core_banking.repository;

import io.github.aminedelta.core_banking.domain.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    Optional<Account> findAndLockById(UUID id);
}

